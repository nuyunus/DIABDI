package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.SantriApp
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceSession
import com.example.data.model.Student
import com.example.data.model.UnitSchedule
import com.example.data.model.UserAccount
import com.example.data.model.UserRole
import com.example.data.repository.SantriRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ScanFeedback {
    data class Success(val record: AttendanceRecord, val student: Student) : ScanFeedback()
    data class AlreadyRecorded(val message: String, val student: Student?) : ScanFeedback()
    data class SessionLocked(val message: String, val session: AttendanceSession) : ScanFeedback()
    data class UnitMismatch(val message: String, val student: Student) : ScanFeedback()
    data class NotFound(val rawCode: String) : ScanFeedback()
    data class Error(val message: String) : ScanFeedback()
}

@OptIn(ExperimentalCoroutinesApi::class)
class SantriViewModel(
    application: Application,
    private val repository: SantriRepository
) : AndroidViewModel(application) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val todayDateString: String = dateFormat.format(Date())

    // Authentication State (Tersimpan permanen sampai user menekan tombol Logout)
    private val _currentUser = MutableStateFlow<UserAccount?>(repository.getLoggedInUser())
    val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()

    init {
        val savedUser = repository.getLoggedInUser()
        if (savedUser != null) {
            if (savedUser.role == UserRole.WALI_SANTRI && savedUser.studentId != null) {
                viewModelScope.launch {
                    val student = repository.findStudentByQrId(savedUser.studentId)
                    _parentChildStudent.value = student
                    _selectedStudentForAi.value = student
                }
            }
        }
        // Auto pull update data di background
        viewModelScope.launch {
            try {
                repository.pullStudentsFromSheets()
            } catch (_: Exception) {}
            try {
                repository.pullUsersFromSheets()
            } catch (_: Exception) {}
            try {
                syncSettingsFromCloud()
            } catch (_: Exception) {}
        }
    }

    private val _currentSession = MutableStateFlow(AttendanceSession.defaultSession())
    val currentSession: StateFlow<AttendanceSession> = _currentSession.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Unit Schedules (MTs, MA, SMK)
    private val _unitSchedules = MutableStateFlow(repository.getUnitSchedules())
    val unitSchedules: StateFlow<List<UnitSchedule>> = _unitSchedules.asStateFlow()

    // Current Teacher's Unit Schedule
    fun getCurrentTeacherUnitSchedule(): UnitSchedule? {
        val unit = _currentUser.value?.unitPendidikan ?: return null
        return _unitSchedules.value.find { it.unit.equals(unit, ignoreCase = true) }
    }

    // Check if selected session is currently active (Otomatis terkunci jika melewati waktu yang ditentukan)
    fun isCurrentSessionActive(): Boolean {
        val session = _currentSession.value
        val schedule = getCurrentTeacherUnitSchedule()
        return session.isCurrentlyActive(schedule, ignoreTimeLock = false)
    }

    fun autoSelectActiveSession() {
        val schedule = getCurrentTeacherUnitSchedule()
        val active = AttendanceSession.findActiveSession(schedule)
        if (active != null) {
            _currentSession.value = active
        }
    }

    // Students list (Strictly filtered by Teacher's Unit if logged in as Guru!)
    val studentsList: StateFlow<List<Student>> = combine(_searchQuery, _currentUser) { query, user ->
        Pair(query, user)
    }.flatMapLatest { (query, user) ->
        if (user?.role == UserRole.GURU && user.unitPendidikan != null) {
            repository.getStudentsByUnit(user.unitPendidikan)
        } else {
            repository.searchStudents(query)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current Session records (Filtered by Teacher's Unit)
    val currentSessionRecords: StateFlow<List<AttendanceRecord>> = combine(_currentSession, _currentUser) { session, user ->
        Pair(session, user)
    }.flatMapLatest { (session, user) ->
        repository.getRecordsByDateAndSession(todayDateString, session.name)
    }.combine(_currentUser) { list, user ->
        if (user?.role == UserRole.GURU && user.unitPendidikan != null) {
            list.filter { it.unitPendidikan.equals(user.unitPendidikan, ignoreCase = true) }
        } else {
            list
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Child records (Specifically for Wali Santri)
    private val _parentChildStudent = MutableStateFlow<Student?>(null)
    val parentChildStudent: StateFlow<Student?> = _parentChildStudent.asStateFlow()

    val parentChildAttendanceRecords: StateFlow<List<AttendanceRecord>> = _parentChildStudent.flatMapLatest { child ->
        if (child != null) {
            repository.getRecordsByStudentId(child.id)
        } else {
            MutableStateFlow(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val todayAllRecords: StateFlow<List<AttendanceRecord>> = repository.getRecordsByDate(todayDateString).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allAttendanceRecords: StateFlow<List<AttendanceRecord>> = repository.allAttendance.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val unsyncedCount: StateFlow<Int> = repository.unsyncedCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _scanFeedback = MutableStateFlow<ScanFeedback?>(null)
    val scanFeedback: StateFlow<ScanFeedback?> = _scanFeedback.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    // AI Evaluation state
    private val _selectedStudentForAi = MutableStateFlow<Student?>(null)
    val selectedStudentForAi: StateFlow<Student?> = _selectedStudentForAi.asStateFlow()

    private val _aiEvaluationResult = MutableStateFlow<String?>(null)
    val aiEvaluationResult: StateFlow<String?> = _aiEvaluationResult.asStateFlow()

    private val _isGeneratingAi = MutableStateFlow(false)
    val isGeneratingAi: StateFlow<Boolean> = _isGeneratingAi.asStateFlow()

    // Cloud Setup state
    private val _scriptUrl = MutableStateFlow(repository.sheetsService.getScriptUrl())
    val scriptUrl: StateFlow<String> = _scriptUrl.asStateFlow()

    private val _cachedUsers = MutableStateFlow<List<UserAccount>>(repository.getCachedUsers())
    val cachedUsers: StateFlow<List<UserAccount>> = _cachedUsers.asStateFlow()

    private val _testConnectionStatus = MutableStateFlow<Pair<Boolean, String>?>(null)
    val testConnectionStatus: StateFlow<Pair<Boolean, String>?> = _testConnectionStatus.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    fun login(account: UserAccount) {
        _currentUser.value = account
        repository.saveLoggedInUser(account)
        if (account.role == UserRole.WALI_SANTRI && account.studentId != null) {
            viewModelScope.launch {
                val student = repository.findStudentByQrId(account.studentId)
                _parentChildStudent.value = student
                _selectedStudentForAi.value = student
            }
        }
    }

    fun loginWithCredentials(
        usernameInput: String,
        passwordInput: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val u = usernameInput.trim()
        val p = passwordInput.trim()

        if (u.isBlank()) {
            onResult(false, "Masukkan Username atau ID Santri.")
            return
        }

        viewModelScope.launch {
            _isLoggingIn.value = true
            try {
                // 1. CEK AKUN DEFAULT / SUPER ADMIN BAWAAN
                val defaultMatch = UserAccount.DEFAULT_ACCOUNTS.find { acc ->
                    acc.username.equals(u, ignoreCase = true) &&
                    (acc.password == p || (acc.role == UserRole.SUPER_ADMIN && (p == "admin" || p == "123" || p == "admin123")))
                }
                if (defaultMatch != null) {
                    login(defaultMatch)
                    _isLoggingIn.value = false
                    onResult(true, null)
                    return@launch
                }

                // 2. CEK CACHE LOKAL USERS (Guru/Admin yang pernah ditarik dari Google Sheets)
                val cachedMatch = repository.getCachedUsers().find { acc ->
                    acc.username.equals(u, ignoreCase = true) && acc.password == p
                }
                if (cachedMatch != null) {
                    login(cachedMatch)
                    _isLoggingIn.value = false
                    onResult(true, null)
                    return@launch
                }

                // 3. CEK SANTRI DARI DATABASE LOKAL (ID sebagai Username & NISN sebagai Password)
                var student = repository.findStudentByIdAndNisn(u, p)
                if (student == null && repository.getStudentCountDirect() == 0) {
                    // Jika database lokal masih kosong (baru pertama kali buka aplikasi),
                    // tarik data siswa langsung dari Google Sheets
                    try {
                        repository.pullStudentsFromSheets()
                        student = repository.findStudentByIdAndNisn(u, p)
                    } catch (_: Exception) {}
                }

                if (student != null) {
                    val studentAccount = UserAccount(
                        username = student.id,
                        password = student.nisn,
                        displayName = student.name,
                        role = UserRole.WALI_SANTRI,
                        studentId = student.id,
                        unitPendidikan = student.unitPendidikan
                    )
                    login(studentAccount)
                    _isLoggingIn.value = false
                    onResult(true, null)
                    return@launch
                }

                // 4. CEK ONLINE KE GOOGLE SHEETS
                val scriptUrl = repository.sheetsService.getScriptUrl()
                if (scriptUrl.isNotBlank() && scriptUrl.startsWith("http")) {
                    val onlineResult = repository.sheetsService.loginOnline(u, p)
                    if (onlineResult.isSuccess) {
                        val user = onlineResult.getOrThrow()
                        login(user)
                        _isLoggingIn.value = false
                        onResult(true, null)
                        return@launch
                    } else {
                        val err = onlineResult.exceptionOrNull()?.message ?: ""
                        if (err.contains("Username atau Password salah", ignoreCase = true)) {
                            _isLoggingIn.value = false
                            onResult(false, "Username atau Password tidak cocok.")
                            return@launch
                        }
                    }
                }

                // 5. Cek ulang santri lokal jika sebelumnya database baru ditarik
                val refreshedStudent = repository.findStudentByIdAndNisn(u, p)
                if (refreshedStudent != null) {
                    val studentAccount = UserAccount(
                        username = refreshedStudent.id,
                        password = refreshedStudent.nisn,
                        displayName = refreshedStudent.name,
                        role = UserRole.WALI_SANTRI,
                        studentId = refreshedStudent.id,
                        unitPendidikan = refreshedStudent.unitPendidikan
                    )
                    login(studentAccount)
                    _isLoggingIn.value = false
                    onResult(true, null)
                    return@launch
                }

                // 6. Cek jika ID santri ada tetapi NISN salah
                val studentById = repository.findStudentByQrId(u)
                if (studentById != null) {
                    _isLoggingIn.value = false
                    onResult(false, "ID Santri '${studentById.name}' ditemukan, namun NISN (kata sandi) salah.")
                    return@launch
                }

                _isLoggingIn.value = false
                onResult(
                    false,
                    "Akun tidak ditemukan atau kata sandi salah. Silakan periksa kembali."
                )
            } catch (e: Exception) {
                _isLoggingIn.value = false
                onResult(false, "Terjadi kesalahan: ${e.localizedMessage ?: "Gagal memproses login"}")
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        repository.clearLoggedInUser()
        _scanFeedback.value = null
        _parentChildStudent.value = null
    }

    fun selectSession(session: AttendanceSession) {
        _currentSession.value = session
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearFeedback() {
        _scanFeedback.value = null
    }

    fun clearScanFeedback() {
        _scanFeedback.value = null
    }

    /**
     * Scan QR: Diproses murni berdasarkan ID santri.
     * Menerapkan validasi:
     * 1. Waktu absensi otomatis terkunci jika melewati waktu yang telah ditentukan (hanya bisa absen di waktu tersebut)
     * 2. Guru hanya boleh mengabsen santri di unit pendidikan guru tersebut (tidak bisa lintas unit)
     */
    fun processBarcode(rawId: String, status: String = "Hadir") {
        viewModelScope.launch {
            val user = _currentUser.value
            val session = _currentSession.value
            val unitSchedule = getCurrentTeacherUnitSchedule()

            // 1. Waktu absensi otomatis terkunci jika melewati waktu yang telah ditentukan
            if (!session.isCurrentlyActive(unitSchedule, ignoreTimeLock = false)) {
                val timeRange = session.getEffectiveTimeRange(unitSchedule)
                _scanFeedback.value = ScanFeedback.SessionLocked(
                    "WAKTU ABSEN TERKUNCI!\nSesi ${session.name} hanya dibuka pada rentang waktu $timeRange. Santri hanya bisa absen pada waktu tersebut.",
                    session
                )
                return@launch
            }

            // 2. Ambil data santri berdasarkan ID
            val student = repository.findStudentByQrId(rawId)
            if (student == null) {
                _scanFeedback.value = ScanFeedback.NotFound(rawId)
                return@launch
            }

            // 3. Validasi Batas Unit: Guru hanya boleh mengabsen santri di unitnya
            if (user?.role == UserRole.GURU && user.unitPendidikan != null) {
                if (!student.unitPendidikan.equals(user.unitPendidikan, ignoreCase = true)) {
                    _scanFeedback.value = ScanFeedback.UnitMismatch(
                        "AKSES DITOLAK: Santri ${student.name} terdaftar di Unit ${student.unitPendidikan}. Anda adalah Guru Unit ${user.unitPendidikan} dan TIDAK BISA mengabsen santri lintas unit.",
                        student
                    )
                    return@launch
                }
            }

            val scannedBy = user?.displayName ?: "Guru Piket"

            val result = repository.recordAttendance(
                student = student,
                session = session,
                status = status,
                scannedBy = scannedBy
            )

            result.onSuccess { record ->
                _scanFeedback.value = ScanFeedback.Success(record, student)
            }.onFailure { error ->
                _scanFeedback.value = ScanFeedback.AlreadyRecorded(error.message ?: "Sudah tercatat", student)
            }
        }
    }

    private val _isRecordingBulk = MutableStateFlow(false)
    val isRecordingBulk: StateFlow<Boolean> = _isRecordingBulk.asStateFlow()

    private val _bulkRecordResult = MutableStateFlow<String?>(null)
    val bulkRecordResult: StateFlow<String?> = _bulkRecordResult.asStateFlow()

    fun clearBulkRecordResult() {
        _bulkRecordResult.value = null
    }

    fun recordBulkManualAttendance(
        students: List<Student>,
        session: AttendanceSession,
        status: String,
        notes: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (students.isEmpty()) {
            onComplete(false, "Pilih setidaknya 1 santri untuk diabsen.")
            return
        }

        viewModelScope.launch {
            _isRecordingBulk.value = true
            val user = _currentUser.value
            val scannedBy = user?.displayName ?: "Guru Piket"

            val (successCount, failCount, messages) = repository.recordBulkManualAttendance(
                students = students,
                session = session,
                status = status,
                notes = notes,
                scannedBy = scannedBy
            )
            _isRecordingBulk.value = false
            val msg = if (failCount == 0) {
                "Berhasil mencatat $successCount santri ($status) untuk sesi ${session.name}!"
            } else {
                "Tercatat $successCount santri, $failCount gagal: ${messages.take(2).joinToString(", ")}"
            }
            _bulkRecordResult.value = msg
            onComplete(successCount > 0, msg)
        }
    }

    fun syncPendingRecords() {
        viewModelScope.launch {
            _isSyncing.value = true
            val count = repository.syncAllPending()
            _isSyncing.value = false
            _syncMessage.value = if (count > 0) "Berhasil menyinkronkan $count data ke Google Sheets" else "Semua data sudah tersinkron"
        }
    }

    private val _isPulling = MutableStateFlow(false)
    val isPulling: StateFlow<Boolean> = _isPulling.asStateFlow()

    private val _pullResult = MutableStateFlow<String?>(null)
    val pullResult: StateFlow<String?> = _pullResult.asStateFlow()

    fun pullStudentsFromSheets(onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isPulling.value = true
            val result = repository.pullStudentsFromSheets()
            _isPulling.value = false
            _cachedUsers.value = repository.getCachedUsers()
            result.onSuccess { count ->
                val userCount = _cachedUsers.value.size
                val msg = "Berhasil memuat $count santri" + (if (userCount > 0) " & $userCount akun users" else "") + " dari Google Sheets"
                _pullResult.value = msg
                onComplete?.invoke(true, msg)
            }.onFailure { err ->
                val msg = err.message ?: "Gagal memuat data dari Google Sheets"
                _pullResult.value = msg
                onComplete?.invoke(false, msg)
            }
        }
    }

    fun pullUsersFromSheets(onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isPulling.value = true
            val result = repository.pullUsersFromSheets()
            _isPulling.value = false
            result.onSuccess { users ->
                _cachedUsers.value = users
                val msg = "Berhasil memuat ${users.size} akun dari sheet Users"
                _pullResult.value = msg
                onComplete?.invoke(true, msg)
            }.onFailure { err ->
                val msg = err.message ?: "Gagal memuat data sheet Users"
                _pullResult.value = msg
                onComplete?.invoke(false, msg)
            }
        }
    }

    private val _isSettingsSyncing = MutableStateFlow(false)
    val isSettingsSyncing: StateFlow<Boolean> = _isSettingsSyncing.asStateFlow()

    private val _settingsSyncResult = MutableStateFlow<String?>(null)
    val settingsSyncResult: StateFlow<String?> = _settingsSyncResult.asStateFlow()

    fun syncSettingsFromCloud(onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isSettingsSyncing.value = true
            val result = repository.fetchSettingsFromCloud()
            _isSettingsSyncing.value = false
            result.onSuccess { payload ->
                if (payload.unitSchedules.isNotEmpty()) {
                    _unitSchedules.value = payload.unitSchedules
                    repository.saveUnitSchedules(payload.unitSchedules)
                }
                if (payload.sessions.isNotEmpty()) {
                    repository.saveSessionSettings(payload.sessions)
                }
                autoSelectActiveSession()
                val msg = "Jadwal berhasil disinkronkan dari sheet Settings Google Sheets!"
                _settingsSyncResult.value = msg
                onComplete?.invoke(true, msg)
            }.onFailure { err ->
                val msg = err.message ?: "Gagal memuat jadwal dari sheet Settings"
                _settingsSyncResult.value = msg
                onComplete?.invoke(false, msg)
            }
        }
    }

    fun updateUnitSchedule(
        unit: String,
        masukStart: String,
        masukEnd: String,
        toleransi: Int,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val current = _unitSchedules.value.toMutableList()
        val index = current.indexOfFirst { it.unit.equals(unit, ignoreCase = true) }
        val updated = UnitSchedule(
            unit = unit,
            masukStart = masukStart,
            masukEnd = masukEnd,
            toleransiMenit = toleransi,
            info = when (unit) {
                "MTs" -> "Madrasah Tsanawiyah"
                "MA" -> "Madrasah Aliyah"
                else -> "Sekolah Menengah Kejuruan"
            }
        )
        if (index != -1) {
            current[index] = updated
        } else {
            current.add(updated)
        }
        _unitSchedules.value = current
        repository.saveUnitSchedules(current)

        // Simpan langsung ke sheet Settings di Google Sheets agar tersinkronisasi terpusat
        viewModelScope.launch {
            val (success, msg) = repository.saveSettingsToCloud(current)
            onComplete?.invoke(success, msg)
        }
    }

    fun selectStudentForAi(student: Student?) {
        _selectedStudentForAi.value = student
        _aiEvaluationResult.value = null
    }

    fun generateAiEvaluation(customNotes: String = "") {
        val student = _selectedStudentForAi.value ?: return
        viewModelScope.launch {
            _isGeneratingAi.value = true
            val records = repository.getRecordsByStudentIdDirect(student.id)
            val result = repository.aiService.generateDisciplineEvaluation(student, records, customNotes)
            _aiEvaluationResult.value = result
            _isGeneratingAi.value = false
        }
    }

    fun updateScriptUrl(url: String) {
        val normalized = repository.sheetsService.normalizeScriptUrl(url)
        _scriptUrl.value = normalized
        repository.sheetsService.setScriptUrl(normalized)
    }

    fun recordManualAttendance(student: Student, status: String = "Hadir", scannedBy: String = "Guru Piket") {
        viewModelScope.launch {
            val result = repository.recordAttendance(
                student = student,
                session = _currentSession.value,
                status = status,
                scannedBy = scannedBy
            )
            result.onSuccess { record ->
                _scanFeedback.value = ScanFeedback.Success(record, student)
            }.onFailure { error ->
                _scanFeedback.value = ScanFeedback.AlreadyRecorded(error.message ?: "Sudah tercatat", student)
            }
        }
    }

    fun addNewStudent(student: Student) {
        viewModelScope.launch {
            repository.insertStudent(student)
        }
    }

    fun deleteStudent(id: String) {
        viewModelScope.launch {
            repository.deleteStudent(id)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testConnectionStatus.value = null
            val result = repository.sheetsService.testConnection(_scriptUrl.value)
            _testConnectionStatus.value = result
            _isTestingConnection.value = false
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = application as SantriApp
                    return SantriViewModel(app, app.repository) as T
                }
            }
    }
}
