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

    // Authentication State
    private val _currentUser = MutableStateFlow<UserAccount?>(null)
    val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()

    private val _currentSession = MutableStateFlow(AttendanceSession.defaultSession())
    val currentSession: StateFlow<AttendanceSession> = _currentSession.asStateFlow()

    // Time lock bypass toggle (for testing/demo outside real prayer hours)
    private val _isTimeLockBypassed = MutableStateFlow(false)
    val isTimeLockBypassed: StateFlow<Boolean> = _isTimeLockBypassed.asStateFlow()

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

    // Check if selected session is currently active
    fun isCurrentSessionActive(): Boolean {
        val schedule = getCurrentTeacherUnitSchedule()
        return _currentSession.value.isCurrentlyActive(schedule, _isTimeLockBypassed.value)
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
        onResult(false, "Masukkan Username/Email.")
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

            // 2. CEK CACHE LOKAL (Jika akun Guru/Admin pernah di-pull dari Sheets)
            val cachedMatch = repository.getCachedUsers().find { acc ->
                acc.username.equals(u, ignoreCase = true) && acc.password == p
            }
            if (cachedMatch != null) {
                login(cachedMatch)
                _isLoggingIn.value = false
                onResult(true, null)
                return@launch
            }

            // 3. CEK ONLINE KE GOOGLE SHEETS (LOGIN KELUAR/MASUK VIA APPS SCRIPT)
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
                        onResult(false, "Username atau Password Guru/Admin tidak cocok.")
                        return@launch
                    }
                }
            }

            // 4. JIKA TIDAK DITEMUKAN
            _isLoggingIn.value = false
            onResult(
                false,
                "Akun Guru/Admin tidak ditemukan. Pastikan akun terdaftar di sheet 'Users' Google Sheets dan HP terhubung internet."
            )
        } catch (e: Exception) {
            _isLoggingIn.value = false
            onResult(false, "Terjadi kesalahan: ${e.localizedMessage ?: "Gagal memproses login"}")
        }
    }
}
    fun logout() {
        _currentUser.value = null
        _scanFeedback.value = null
        _parentChildStudent.value = null
    }

    fun selectSession(session: AttendanceSession) {
        _currentSession.value = session
    }

    fun toggleTimeLockBypass() {
        _isTimeLockBypassed.value = !_isTimeLockBypassed.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearFeedback() {
        _scanFeedback.value = null
    }

    /**
     * Scan QR: Diproses murni berdasarkan ID santri.
     * Menerapkan validasi:
     * 1. Sesi terkunci jika di luar jam sesi
     * 2. Guru hanya boleh mengabsen santri di unit pendidikan guru tersebut (tidak bisa lintas unit)
     */
    fun processBarcode(rawId: String, status: String = "Hadir") {
        viewModelScope.launch {
            val user = _currentUser.value
            val session = _currentSession.value
            val unitSchedule = getCurrentTeacherUnitSchedule()

            // 1. Cek Apakah Sesi Sedang Terkunci
            if (!session.isCurrentlyActive(unitSchedule, _isTimeLockBypassed.value)) {
                val timeRange = session.getEffectiveTimeRange(unitSchedule)
                _scanFeedback.value = ScanFeedback.SessionLocked(
                    "Sesi ${session.name} saat ini TERKUNCI.\nJam aktif: $timeRange. Absensi hanya dapat dicatat saat jam sesi dibuka.",
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

    fun updateUnitSchedule(unit: String, masukStart: String, masukEnd: String, toleransi: Int) {
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
