package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey
    val id: String, // ID Santri (contoh: "532303019"), Primary Key & QR Code Payload
    val nisn: String = "",
    val name: String, // Nama
    val gender: String = "Laki-laki", // Jenis Kelamin ("Laki-laki" atau "Perempuan")
    val className: String = "-", // Kelas (contoh: "7A", "10-TKJ")
    val unitPendidikan: String = "MTs", // Unit Pendidikan: "MTs", "MA", "SMK"
    val dormitory: String = "-", // Nama Asrama
    val parentName: String = "-", // Nama Ortu
    val parentPhone: String = "", // No WA Ortu (contoh: "628123456789")
    val status: String = "Siswa"
)
