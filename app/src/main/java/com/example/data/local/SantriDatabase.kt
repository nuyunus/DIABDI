package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AttendanceRecord
import com.example.data.model.Student

@Database(
    entities = [Student::class, AttendanceRecord::class],
    version = 1,
    exportSchema = false
)
abstract class SantriDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: SantriDatabase? = null

        fun getInstance(context: Context): SantriDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SantriDatabase::class.java,
                    "santri_absensi.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
