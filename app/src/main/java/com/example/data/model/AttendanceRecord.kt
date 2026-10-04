package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_records",
    indices = [
        Index(value = ["studentId", "sessionName", "date"], unique = true),
        Index(value = ["date", "sessionName"]),
        Index(value = ["unitPendidikan"]),
        Index(value = ["isSynced"])
    ]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val studentId: String, // ID Santri (contoh: "SAN-001"), diambil dari QR
    val studentName: String, // Nama Siswa
    val className: String, // Kelas
    val unitPendidikan: String, // Unit Pendidikan: MTs, MA, SMK
    val dormitory: String, // Nama Asrama
    val sessionName: String, // Sesi (7 Sesi Harian)
    val date: String, // "yyyy-MM-dd"
    val time: String, // "HH:mm:ss"
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Hadir", // Hadir, Terlambat, Izin, Sakit, Alpa
    val scannedBy: String = "Guru Piket", // Petugas Scan
    val notes: String = "", // Catatan
    val isSynced: Boolean = false,
    val syncError: String? = null,
    val parentPhone: String = "" // No WA Ortu untuk notifikasi
)
