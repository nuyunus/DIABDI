package com.example.data.repository

import android.content.Context
import com.example.data.local.AttendanceDao
import com.example.data.local.InitialData
import com.example.data.local.StudentDao
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceSession
import com.example.data.model.Student
import com.example.data.model.UnitSchedule
import com.example.data.model.UserAccount
import com.example.data.remote.GeminiAiService
import com.example.data.remote.GoogleSheetsSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SantriRepository(
    private val context: Context,
    private val studentDao: StudentDao,
    private val attendanceDao: AttendanceDao,
    val sheetsService: GoogleSheetsSyncService,
    val aiService: GeminiAiService
) {

    private val schedulePrefs = context.getSharedPreferences("unit_schedules_prefs", Context.MODE_PRIVATE)
    private val userPrefs = context.getSharedPreferences("cached_users_prefs", Context.MODE_PRIVATE)

    val allStudents: Flow<List<Student>> = studentDao.getAllStudents()
    val allAttendance: Flow<List<AttendanceRecord>> = attendanceDao.getAllRecords()
    val unsyncedCount: Flow<Int> = attendanceDao.getUnsyncedCount()

    fun getRecordsByDateAndSession(date: String, session: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getRecordsByDateAndSession(date, session)
    }

    fun getRecordsByDate(date: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getRecordsByDate(date)
    }

    fun getRecordsByStudentId(studentId: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getRecordsByStudentId(studentId)
    }

    suspend fun getRecordsByStudentIdDirect(studentId: String): List<AttendanceRecord> {
        return attendanceDao.getRecordsByStudentIdDirect(studentId)
    }

    fun searchStudents(query: String): Flow<List<Student>> {
        return if (query.isBlank()) {
            studentDao.getAllStudents()
        } else {
            studentDao.searchStudents(query.trim())
        }
    }

    fun getStudentsByUnit(unit: String): Flow<List<Student>> {
        return studentDao.getStudentsByUnit(unit)
    }

    suspend fun findStudentByQrId(rawQr: String): Student? {
        val clean = rawQr.trim()
        val id = if (clean.startsWith("ID:")) {
            clean.removePrefix("ID:").trim()
        } else {
            clean
        }
        return studentDao.getStudentById(id) ?: studentDao.findByIdOrNisn(id)
    }

    suspend fun findStudentByIdAndNisn(idInput: String, nisnInput: String): Student? {
        val cleanId = idInput.trim()
        val cleanNisn = nisnInput.trim()
        if (cleanId.isBlank()) return null

        // 1. Direct query id and nisn
        val match = studentDao.getStudentByIdAndNisn(cleanId, cleanNisn)
        if (match != null) return match

        // 2. Query by ID, then verify NISN ignoring leading zeros or spaces
        val studentById = studentDao.getStudentById(cleanId)
        if (studentById != null) {
            val sNisn = studentById.nisn.trim()
            if (sNisn.equals(cleanNisn, ignoreCase = true) ||
                sNisn.trimStart('0') == cleanNisn.trimStart('0') ||
                cleanNisn.isBlank() ||
                studentById.id == cleanNisn
            ) {
                return studentById
            }
        }

        // 3. User might have swapped ID and NISN
        val studentByNisn = studentDao.getStudentByNisn(cleanId)
        if (studentByNisn != null) {
            val sId = studentByNisn.id.trim()
            if (sId.equals(cleanNisn, ignoreCase = true) || cleanNisn.isBlank()) {
                return studentByNisn
            }
        }

        return null
    }

    suspend fun recordAttendance(
        student: Student,
        session: AttendanceSession,
        status: String = "Hadir",
        scannedBy: String = "Guru Piket"
    ): Result<AttendanceRecord> = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val now = Date()
        val todayStr = dateFormat.format(now)
        val timeStr = timeFormat.format(now)

        // Cek duplikasi absensi berdasarkan ID santri pada sesi dan tanggal yang sama
        val existing = attendanceDao.getRecordForStudentSession(student.id, session.name, todayStr)
        if (existing != null) {
            return@withContext Result.failure(
                IllegalStateException("${student.name} (${student.id}) sudah tercatat di sesi ${session.name} pada jam ${existing.time}")
            )
        }

        val record = AttendanceRecord(
            studentId = student.id,
            studentName = student.name,
            className = student.className,
            unitPendidikan = student.unitPendidikan,
            dormitory = student.dormitory,
            sessionName = session.name,
            date = todayStr,
            time = timeStr,
            timestamp = now.time,
            status = status,
            scannedBy = scannedBy,
            isSynced = false,
            parentPhone = student.parentPhone
        )

        val id = attendanceDao.insertRecord(record)
        val savedRecord = record.copy(id = id)

        // Kirim langsung ke Google Sheets secara real-time
        val (syncOk, syncMsg) = sheetsService.syncAttendance(savedRecord)
        if (syncOk) {
            attendanceDao.markAsSynced(id)
            return@withContext Result.success(savedRecord.copy(isSynced = true))
        } else {
            attendanceDao.markSyncFailed(id, syncMsg)
            return@withContext Result.success(savedRecord.copy(isSynced = false, syncError = syncMsg))
        }
    }

    suspend fun syncAllPending(): Int = withContext(Dispatchers.IO) {
        val unsynced = attendanceDao.getUnsyncedRecords()
        var successCount = 0
        for (item in unsynced) {
            val (ok, err) = sheetsService.syncAttendance(item)
            if (ok) {
                attendanceDao.markAsSynced(item.id)
                successCount++
            } else {
                attendanceDao.markSyncFailed(item.id, err)
            }
        }
        successCount
    }

    /**
     * Menghubungkan langsung ke database Google Sheets dan mengunduh data santri riil
     */
    suspend fun pullStudentsFromSheets(): Result<Int> = withContext(Dispatchers.IO) {
        val result = sheetsService.fetchStudentsFromSheets()
        result.map { remoteStudents ->
            // Bersihkan seluruh data dummy / santri lokal lama sehingga murni hanya data asli dari Google Sheets
            studentDao.deleteAllStudents()
            if (remoteStudents.isNotEmpty()) {
                studentDao.insertStudents(remoteStudents)
            }
            // Sekaligus sinkronkan daftar users dari sheet Users
            try {
                pullUsersFromSheets()
            } catch (_: Exception) {}
            remoteStudents.size
        }
    }

    /**
     * Menarik daftar Users (Guru & Admin) langsung dari sheet Users di Google Sheets
     */
    suspend fun pullUsersFromSheets(): Result<List<UserAccount>> = withContext(Dispatchers.IO) {
        val result = sheetsService.fetchUsersFromSheets()
        result.onSuccess { users ->
            if (users.isNotEmpty()) {
                saveCachedUsers(users)
            }
        }
        result
    }

    fun getCachedUsers(): List<UserAccount> {
        val jsonStr = userPrefs.getString("users_list_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<UserAccount>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val roleStr = obj.optString("role", "GURU")
                val role = when {
                    roleStr.contains("SUPER") -> com.example.data.model.UserRole.SUPER_ADMIN
                    roleStr.contains("ADMIN") -> com.example.data.model.UserRole.ADMIN
                    else -> com.example.data.model.UserRole.GURU
                }
                list.add(
                    UserAccount(
                        username = obj.getString("username"),
                        password = obj.optString("password", "123"),
                        displayName = obj.optString("displayName", obj.getString("username")),
                        role = role,
                        unitPendidikan = obj.optString("unitPendidikan", null).takeIf { !it.isNullOrBlank() }
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedUsers(users: List<UserAccount>) {
        try {
            val arr = JSONArray()
            for (u in users) {
                val obj = JSONObject().apply {
                    put("username", u.username)
                    put("password", u.password)
                    put("displayName", u.displayName)
                    put("role", u.role.name)
                    put("unitPendidikan", u.unitPendidikan ?: "")
                }
                arr.put(obj)
            }
            userPrefs.edit().putString("users_list_json", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    suspend fun insertStudent(student: Student) {
        studentDao.insertStudent(student)
    }

    suspend fun deleteStudent(id: String) {
        studentDao.deleteStudent(id)
    }

    // Unit Schedule Management (MTs, MA, SMK)
    fun getUnitSchedules(): List<UnitSchedule> {
        val jsonStr = schedulePrefs.getString("unit_schedules_json", null)
        if (jsonStr.isNullOrBlank()) {
            return AttendanceSession.DEFAULT_UNIT_SCHEDULES
        }
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<UnitSchedule>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UnitSchedule(
                        unit = obj.getString("unit"),
                        masukStart = obj.getString("masukStart"),
                        masukEnd = obj.getString("masukEnd"),
                        toleransiMenit = obj.optInt("toleransiMenit", 10),
                        info = obj.optString("info", "")
                    )
                )
            }
            if (list.isNotEmpty()) list else AttendanceSession.DEFAULT_UNIT_SCHEDULES
        } catch (_: Exception) {
            AttendanceSession.DEFAULT_UNIT_SCHEDULES
        }
    }

    fun saveUnitSchedules(schedules: List<UnitSchedule>) {
        val jsonArray = JSONArray()
        for (item in schedules) {
            val obj = JSONObject().apply {
                put("unit", item.unit)
                put("masukStart", item.masukStart)
                put("masukEnd", item.masukEnd)
                put("toleransiMenit", item.toleransiMenit)
                put("info", item.info)
            }
            jsonArray.put(obj)
        }
        schedulePrefs.edit().putString("unit_schedules_json", jsonArray.toString()).apply()
    }

    suspend fun initializeDatabase() = withContext(Dispatchers.IO) {
        // Hapus data dummy: jika script URL sudah terkonfigurasi, langsung sinkronkan santri & users dari Google Sheets
        if (sheetsService.getScriptUrl().isNotBlank()) {
            pullStudentsFromSheets()
            try {
                pullUsersFromSheets()
            } catch (_: Exception) {}
        }
    }
}
