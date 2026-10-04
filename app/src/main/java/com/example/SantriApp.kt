package com.example

import android.app.Application
import com.example.data.local.SantriDatabase
import com.example.data.remote.GeminiAiService
import com.example.data.remote.GoogleSheetsSyncService
import com.example.data.repository.SantriRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SantriApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { SantriDatabase.getInstance(this) }
    val sheetsService by lazy { GoogleSheetsSyncService(this) }
    val aiService by lazy { GeminiAiService() }

    val repository by lazy {
        SantriRepository(
            context = this,
            studentDao = database.studentDao(),
            attendanceDao = database.attendanceDao(),
            sheetsService = sheetsService,
            aiService = aiService
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            repository.initializeDatabase()
        }
    }
}
