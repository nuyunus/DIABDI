package com.example.data.local

import com.example.data.model.AttendanceRecord
import com.example.data.model.Student

object InitialData {

    // Tanpa data dummy - aplikasi murni memuat data asli langsung dari Google Sheets
    val realInitialStudents: List<Student> = emptyList()

    // Kosongkan riwayat absensi dummy - pengujian murni dari database asli
    fun generateInitialAttendance(): List<AttendanceRecord> {
        return emptyList()
    }
}
