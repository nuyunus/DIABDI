package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AttendanceRecord
import com.example.data.model.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateDisciplineEvaluation(
        student: Student,
        records: List<AttendanceRecord>,
        customNotes: String = ""
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        val totalRecords = records.size
        val hadirCount = records.count { it.status.equals("Hadir", ignoreCase = true) }
        val terlambatCount = records.count { it.status.equals("Terlambat", ignoreCase = true) }
        val izinCount = records.count { it.status.equals("Izin", ignoreCase = true) || it.status.equals("Sakit", ignoreCase = true) }
        val subuhRecords = records.filter { it.sessionName.contains("Subuh", ignoreCase = true) }
        val subuhHadir = subuhRecords.count { it.status.equals("Hadir", ignoreCase = true) }
        val subuhTerlambat = subuhRecords.count { it.status.equals("Terlambat", ignoreCase = true) }

        val attendancePercentage = if (totalRecords > 0) (hadirCount * 100) / totalRecords else 100

        val prompt = """
            Anda adalah Konsultan Pendidikan Pesantren & Dewan Pembina Santri yang bijaksana, santun, dan islami.
            Tugas Anda: Buatlah "Narasi Evaluasi Kedisiplinan & Catatan Perkembangan Santri" bulanan untuk orang tua/wali santri berdasarkan data absensi riil berikut:
            
            Profil Santri:
            - ID Santri: ${student.id}
            - NISN: ${student.nisn}
            - Nama: ${student.name}
            - Unit Pendidikan: ${student.unitPendidikan}
            - Kelas: ${student.className}
            - Asrama: ${student.dormitory}
            - Nama Wali: ${student.parentName}
            
            Statistik 7 Sesi Harian:
            - Total Transaksi Absensi: $totalRecords
            - Hadir Tepat Waktu: $hadirCount ($attendancePercentage%)
            - Terlambat: $terlambatCount
            - Izin/Sakit: $izinCount
            - Sholat Subuh Berjamaah: $subuhHadir Hadir, $subuhTerlambat Terlambat dari ${subuhRecords.size} catatan
            - Catatan Khusus Pengasuh: ${customNotes.ifBlank { "Ananda aktif mengikuti taklim dan taat aturan asrama." }}
            
            Format Output yang Harus Dihasilkan:
            1. Salam hangat pembuka yang santun dan mendoakan kebaikan bagi keluarga santri.
            2. Apresiasi & Poin Positif (khususnya kedisiplinan sholat berjamaah & sekolah unit ${student.unitPendidikan}).
            3. Catatan Evaluasi & Area yang Perlu Ditingkatkan (disampaikan secara halus dan memotivasi).
            4. Rekomendasi Sinergi untuk Orang Tua saat libur/kunjungan wali.
            5. Doa penutup dan kalimat motivasi Qur'ani.
            
            Gunakan Bahasa Indonesia yang elegan, hangat, penuh kasih sayang, dan mudah dibaca melalui WhatsApp.
        """.trimIndent()

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext generateFallbackEvaluation(student, hadirCount, terlambatCount, subuhHadir, attendancePercentage, customNotes)
        }

        try {
            val systemInstructionJson = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "Anda adalah Asisten Pakar Evaluasi Karakter & Kedisiplinan Pesantren dari Google AI Studio. Tulis narasi evaluasi yang islami, santun, profesional, dan menumbuhkan semangat belajar santri.")
                    })
                })
            }

            val contentJson = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", prompt)
                    })
                })
            }

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply { put(contentJson) })
                put("systemInstruction", systemInstructionJson)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.95)
                    put("topK", 40)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBodyJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseString)
                val candidates = json.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text")

                if (!text.isNullOrBlank()) {
                    return@withContext text
                }
            }
            Log.w("GeminiAiService", "API response error: $responseString")
            generateFallbackEvaluation(student, hadirCount, terlambatCount, subuhHadir, attendancePercentage, customNotes)
        } catch (e: Exception) {
            Log.e("GeminiAiService", "Call failed: ${e.message}", e)
            generateFallbackEvaluation(student, hadirCount, terlambatCount, subuhHadir, attendancePercentage, customNotes)
        }
    }

    private fun generateFallbackEvaluation(
        student: Student,
        hadir: Int,
        terlambat: Int,
        subuhHadir: Int,
        percentage: Int,
        notes: String
    ): String {
        val grade = when {
            percentage >= 90 -> "Sangat Baik (Mumtaz)"
            percentage >= 75 -> "Baik (Jayyid Jiddan)"
            else -> "Cukup (Perlu Bimbingan Tambahan)"
        }

        return """
            *LAPORAN EVALUASI KEDISIPLINAN & AKHLAK SANTRI*
            *Pondok Pesantren & Sekolah Terpadu (${student.unitPendidikan})*
            
            Assalamu'alaikum Warahmatullahi Wabarakatuh,
            Yth. ${student.parentName}, orang tua dari ananda *${student.name}* (ID: ${student.id} • Unit: ${student.unitPendidikan} Kelas ${student.className} • ${student.dormitory}).
            
            Semoga Bapak/Ibu sekeluarga senantiasa berada dalam lindungan dan rahmat Allah SWT.
            
            Berikut kami sampaikan ringkasan evaluasi perkembangan kedisiplinan ananda selama periode berjalan:
            
            📊 *Kilas Kedisiplinan 7 Sesi Harian:*
            • Persentase Kehadiran: *${percentage}%* (${grade})
            • Total Sesi Hadir Tepat Waktu: $hadir sesi
            • Catatan Keterlambatan: $terlambat sesi
            • Konsistensi Sholat Subuh: $subuhHadir sesi hadir di masjid
            
            🌟 *Catatan Positif Pengasuh:*
            Ananda *${student.name}* menunjukkan keteladanan yang baik dalam menjaga sholat berjamaah dan ketepatan jam masuk sekolah Unit ${student.unitPendidikan}. Kehidupan sosial di ${student.dormitory} terjalin harmonis dan saling mengingatkan dalam kebaikan.
            
            📌 *Arahan & Rekomendasi:*
            ${if (terlambat > 0) "Perlu sedikit perhatian saat sesi Masuk Asrama dan Sholat Maghrib agar tidak terburu-buru." else "Pertahankan kebiasaan bangun lebih awal dan istiqomah dalam qiyamul lail."}
            ${if (notes.isNotBlank()) "Catatan Khusus: $notes" else ""}
            
            🤲 *Doa Penutup:*
            _“Semoga Allah SWT senantiasa menganugerahkan kepada ananda ilmu yang bermanfaat, akhlak yang mulia, dan menjadikannya qurrata a’yun bagi kedua orang tuanya. Aamiin ya Rabbal ‘Alamin.”_
            
            Wassalamu'alaikum Warahmatullahi Wabarakatuh.
            *Dewan Pengasuhan & Kedisiplinan Santri*
        """.trimIndent()
    }
}
