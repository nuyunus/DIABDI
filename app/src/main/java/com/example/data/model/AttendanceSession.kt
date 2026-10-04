package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.SessionAshar
import com.example.ui.theme.SessionAsrama
import com.example.ui.theme.SessionDzuhur
import com.example.ui.theme.SessionIsya
import com.example.ui.theme.SessionMaghrib
import com.example.ui.theme.SessionSekolah
import com.example.ui.theme.SessionSubuh
import java.util.Calendar

data class UnitSchedule(
    val unit: String, // "MTs", "MA", "SMK"
    val masukStart: String, // contoh "06:30"
    val masukEnd: String, // contoh "07:00"
    val toleransiMenit: Int = 10,
    val info: String = ""
) {
    fun parseStartMinutes(): Int {
        val parts = masukStart.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 6
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
        return h * 60 + m
    }

    fun parseEndMinutes(): Int {
        val parts = masukEnd.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 15
        return h * 60 + m + toleransiMenit
    }
}

data class AttendanceSession(
    val id: Int,
    val name: String,
    val timeRange: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val category: String, // "Ibadah", "Akademik", "Kedisiplinan Asrama"
    val accentColor: Color,
    val iconName: String
) {
    fun isCurrentlyActive(unitSchedule: UnitSchedule?, ignoreTimeLock: Boolean = false): Boolean {
        if (ignoreTimeLock) return true

        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        if (id == 2 && unitSchedule != null) {
            // Sesi Masuk Sekolah: Berdasarkan setting jam unit MTs, MA, atau SMK
            val start = unitSchedule.parseStartMinutes()
            val end = unitSchedule.parseEndMinutes()
            return currentMinutes in start..end
        }

        val start = startHour * 60 + startMinute
        val end = endHour * 60 + endMinute
        return currentMinutes in start..end
    }

    fun getEffectiveTimeRange(unitSchedule: UnitSchedule?): String {
        return if (id == 2 && unitSchedule != null) {
            "${unitSchedule.masukStart} - ${unitSchedule.masukEnd} WIB (Unit ${unitSchedule.unit})"
        } else {
            timeRange
        }
    }

    companion object {
        val ALL_SESSIONS = listOf(
            AttendanceSession(
                id = 1,
                name = "Sholat Subuh",
                timeRange = "04:30 - 05:30 WIB",
                startHour = 4,
                startMinute = 30,
                endHour = 5,
                endMinute = 30,
                category = "Ibadah",
                accentColor = SessionSubuh,
                iconName = "WbTwilight"
            ),
            AttendanceSession(
                id = 2,
                name = "Masuk Sekolah",
                timeRange = "Sesuai Jadwal Unit",
                startHour = 6,
                startMinute = 30,
                endHour = 7,
                endMinute = 30,
                category = "Akademik",
                accentColor = SessionSekolah,
                iconName = "School"
            ),
            AttendanceSession(
                id = 3,
                name = "Sholat Dzuhur",
                timeRange = "12:00 - 13:00 WIB",
                startHour = 12,
                startMinute = 0,
                endHour = 13,
                endMinute = 0,
                category = "Ibadah",
                accentColor = SessionDzuhur,
                iconName = "WbSunny"
            ),
            AttendanceSession(
                id = 4,
                name = "Sholat Ashar",
                timeRange = "15:15 - 16:15 WIB",
                startHour = 15,
                startMinute = 15,
                endHour = 16,
                endMinute = 15,
                category = "Ibadah",
                accentColor = SessionAshar,
                iconName = "BrightnessMedium"
            ),
            AttendanceSession(
                id = 5,
                name = "Masuk Asrama",
                timeRange = "17:00 - 17:45 WIB",
                startHour = 17,
                startMinute = 0,
                endHour = 17,
                endMinute = 45,
                category = "Kedisiplinan Asrama",
                accentColor = SessionAsrama,
                iconName = "HomeWork"
            ),
            AttendanceSession(
                id = 6,
                name = "Sholat Maghrib",
                timeRange = "18:00 - 19:00 WIB",
                startHour = 18,
                startMinute = 0,
                endHour = 19,
                endMinute = 0,
                category = "Ibadah",
                accentColor = SessionMaghrib,
                iconName = "NightsStay"
            ),
            AttendanceSession(
                id = 7,
                name = "Sholat Isya/KBM",
                timeRange = "19:15 - 20:30 WIB",
                startHour = 19,
                startMinute = 15,
                endHour = 20,
                endMinute = 30,
                category = "Ibadah / KBM",
                accentColor = SessionIsya,
                iconName = "Bedtime"
            )
        )

        val DEFAULT_UNIT_SCHEDULES = listOf(
            UnitSchedule(
                unit = "MTs",
                masukStart = "06:30",
                masukEnd = "07:00",
                toleransiMenit = 10,
                info = "Madrasah Tsanawiyah (Kelas 7-9)"
            ),
            UnitSchedule(
                unit = "MA",
                masukStart = "06:45",
                masukEnd = "07:15",
                toleransiMenit = 10,
                info = "Madrasah Aliyah (Kelas 10-12)"
            ),
            UnitSchedule(
                unit = "SMK",
                masukStart = "07:00",
                masukEnd = "07:30",
                toleransiMenit = 15,
                info = "Sekolah Menengah Kejuruan (Praktik/Bengkel)"
            )
        )

        fun defaultSession(): AttendanceSession = ALL_SESSIONS[0]
    }
}
