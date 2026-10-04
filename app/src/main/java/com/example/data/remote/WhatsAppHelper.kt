package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.AttendanceRecord
import java.net.URLEncoder

object WhatsAppHelper {

    fun formatIndonesianPhone(phone: String): String {
        var cleaned = phone.replace(Regex("[^0-9]"), "")
        if (cleaned.startsWith("0")) {
            cleaned = "62" + cleaned.substring(1)
        } else if (!cleaned.startsWith("62") && cleaned.isNotEmpty()) {
            cleaned = "62$cleaned"
        }
        return cleaned
    }

    fun buildAttendanceMessage(record: AttendanceRecord): String {
        val statusEmoji = when (record.status) {
            "Hadir" -> "✅"
            "Terlambat" -> "⚠️"
            "Izin" -> "ℹ️"
            "Sakit" -> "🏥"
            else -> "❌"
        }

        return """
            Assalamu'alaikum Warahmatullahi Wabarakatuh.
            
            Yth. Bapak/Ibu Wali Santri dari:
            🆔 ID Santri: *${record.studentId}*
            👤 Nama: *${record.studentName}*
            🏫 Unit: *${record.unitPendidikan}* (Kelas ${record.className})
            🏠 Asrama: ${record.dormitory}
            
            Alhamdulillah, ananda telah tercatat pada Sistem Absensi Digital Pesantren & Sekolah:
            🕌 Sesi: *${record.sessionName}*
            📅 Tanggal: ${record.date}
            ⏰ Waktu: ${record.time} WIB
            $statusEmoji Status: *${record.status.uppercase()}*
            👮 Petugas Scan: ${record.scannedBy}
            
            _Jazakumullahu khairan katsiran atas doa dan bimbingan Ayah/Bunda untuk istiqomah dan disiplin ananda._
            
            Wassalamu'alaikum Warahmatullahi Wabarakatuh.
            *Tim Pengasuhan & Kedisiplinan Santri*
        """.trimIndent()
    }

    fun openWhatsAppChat(context: Context, phone: String, message: String) {
        val cleanPhone = formatIndonesianPhone(phone)
        try {
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(shareIntent, "Kirim Notifikasi via"))
            } catch (_: Exception) {
                Toast.makeText(context, "Tidak dapat membuka aplikasi WhatsApp", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
