package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.model.AttendanceRecord
import com.example.data.model.Student
import com.example.data.model.UserAccount
import com.example.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GoogleSheetsSyncService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val prefs = context.getSharedPreferences("santri_cloud_prefs", Context.MODE_PRIVATE)

    fun normalizeScriptUrl(url: String): String {
        var raw = url.trim()
        if (raw.isBlank()) return ""
        raw = raw.removeSurrounding("\"").removeSurrounding("'").trim()

        // 1. Deteksi Deployment ID Google Apps Script secara langsung (berawalan AKfycb)
        val akfyMatch = Regex("""(AKfycb[a-zA-Z0-9_\-]+)""").find(raw)
        if (akfyMatch != null) {
            return "https://script.google.com/macros/s/${akfyMatch.value}/exec"
        }

        // 2. Jika user hanya menempelkan path parsial
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            val clean = raw.trimStart('/').removePrefix("s/").removePrefix("macros/s/")
            return if (clean.endsWith("/exec")) {
                "https://script.google.com/macros/s/$clean"
            } else {
                "https://script.google.com/macros/s/$clean/exec"
            }
        }

        // 3. Pastikan berakhiran /exec jika menggunakan URL script.google.com
        if (raw.contains("/macros/s/") && !raw.endsWith("/exec") && !raw.contains("/exec?")) {
            raw = raw.trimEnd('/') + "/exec"
        }
        return raw
    }

    fun getScriptUrl(): String {
        val stored = prefs.getString(KEY_SCRIPT_URL, "") ?: ""
        // Normalisasi URL
        val normalized = normalizeScriptUrl(stored)
        
        // Jika hasil simpanan kosong, rusak, atau bukan URL http(s), otomatis pakai DEFAULT_SCRIPT_URL
        return if (normalized.isBlank() || !normalized.startsWith("http")) {
            DEFAULT_SCRIPT_URL
        } else {
            normalized
        }
    }

    fun setScriptUrl(url: String) {
        val normalized = normalizeScriptUrl(url)
        if (normalized.isNotBlank()) {
            prefs.edit().putString(KEY_SCRIPT_URL, normalized).apply()
        } else {
            // Jika user mengosongkan URL di menu pengaturan, kembalikan ke DEFAULT
            prefs.edit().putString(KEY_SCRIPT_URL, DEFAULT_SCRIPT_URL).apply()
        }
    }

    private fun extractAppsScriptException(html: String): String? {
        val regex = Regex("""Exception:\s*([^<]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(html)
        return match?.groupValues?.get(1)?.trim()
    }

    suspend fun testConnection(url: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val targetUrl = normalizeScriptUrl(url)
        if (targetUrl.isBlank()) {
            return@withContext Pair(false, "URL Google Apps Script belum diisi.")
        }
        try {
            val separator = if (targetUrl.contains("?")) "&" else "?"
            // Uji 1: action=getStudents (format deployed aktif pengguna)
            val testActions = listOf("getStudents", "ping", "get_students")
            var lastResponseBody = ""
            var lastCode = 200

            for (actionName in testActions) {
                try {
                    val reqUrl = "$targetUrl${separator}action=$actionName&t=${System.currentTimeMillis()}"
                    val request = Request.Builder().url(reqUrl).get().build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()?.trim() ?: ""
                    lastResponseBody = body
                    lastCode = response.code

                    val ex = extractAppsScriptException(body)
                    if (ex != null && actionName == testActions.last()) {
                        return@withContext Pair(
                            false,
                            "Google Apps Script Error:\n\"$ex\"\n\nSolusi: Buka panel Super Admin di aplikasi, salin kode Apps Script (Code.gs) terbaru, dan tempelkan ke script.google.com lalu Deploy ulang."
                        )
                    }

                    if (response.isSuccessful && !body.startsWith("<!DOCTYPE") && !body.startsWith("<html") && body.isNotBlank()) {
                        return@withContext Pair(true, "Berhasil Terhubung ke Google Sheets Database!")
                    }
                } catch (te: Exception) {
                    Log.w("GoogleSheetsSync", "Test $actionName failed: ${te.message}")
                }
            }

            // Fallback uji via POST
            try {
                for (actionName in listOf("ping", "record_attendance")) {
                    val postPayload = JSONObject().put("action", actionName).toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                    val postReq = Request.Builder().url(targetUrl).post(postPayload).build()
                    val postRes = client.newCall(postReq).execute()
                    val postBody = postRes.body?.string()?.trim() ?: ""
                    if (postRes.isSuccessful && !postBody.startsWith("<!DOCTYPE") && !postBody.startsWith("<html") && postBody.isNotBlank()) {
                        return@withContext Pair(true, "Berhasil Terhubung ke Google Sheets (Koneksi Aman)!")
                    }
                }
            } catch (_: Exception) {}

            if (lastResponseBody.startsWith("<!DOCTYPE") || lastResponseBody.startsWith("<html")) {
                Pair(
                    false,
                    "Web App mengembalikan halaman Web/Login Google, bukan data JSON.\nSolusi di Apps Script:\n1. Buka Deploy > Manage deployments\n2. Klik Edit (ikon pensil)\n3. Pastikan 'Who has access' dipilih 'Anyone' (Siapa saja)\n4. Klik Deploy dan gunakan URL Web App."
                )
            } else if (lastCode == 404) {
                Pair(
                    false,
                    "HTTP 404: Web App tidak ditemukan pada URL ini.\n\nPastikan URL diakhiri dengan /exec"
                )
            } else {
                Pair(false, "HTTP $lastCode: $lastResponseBody")
            }
        } catch (e: Exception) {
            Log.e("GoogleSheetsSync", "Test failed: ${e.message}", e)
            Pair(false, "Gagal koneksi: ${e.localizedMessage ?: "Cek jaringan internet atau URL deployment"}")
        }
    }

    suspend fun fetchStudentsFromSheets(): Result<List<Student>> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl()
        if (scriptUrl.isBlank() || !scriptUrl.startsWith("http")) {
            return@withContext Result.failure(IllegalStateException("URL Google Sheets belum diatur. Masukkan URL deployment Web App di panel Super Admin."))
        }

        try {
            var resStr = ""
            var fetchSuccess = false

            val sep = if (scriptUrl.contains("?")) "&" else "?"
            // Coba getStudents (camelCase pengguna) dan get_students (snake_case)
            val actions = listOf("getStudents", "get_students")

            for (act in actions) {
                if (fetchSuccess) break
                val getUrl = "$scriptUrl${sep}action=$act&t=${System.currentTimeMillis()}"

                // Percobaan 1: Mengambil data via GET
                try {
                    val request = Request.Builder().url(getUrl).get().build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()?.trim() ?: ""

                    if (response.isSuccessful && !body.startsWith("<!DOCTYPE") && !body.startsWith("<html") && body.isNotBlank()) {
                        resStr = body
                        fetchSuccess = true
                        break
                    }
                } catch (ge: Exception) {
                    Log.w("GoogleSheetsSync", "GET $act warning: ${ge.message}")
                }

                // Percobaan 2: Fallback via POST jika GET gagal
                if (!fetchSuccess) {
                    try {
                        val postPayload = JSONObject().put("action", act).toString()
                            .toRequestBody("application/json; charset=utf-8".toMediaType())
                        val postReq = Request.Builder().url(getUrl).post(postPayload).build()
                        val postResponse = client.newCall(postReq).execute()
                        val postBody = postResponse.body?.string()?.trim() ?: ""

                        if (postResponse.isSuccessful && !postBody.startsWith("<!DOCTYPE") && !postBody.startsWith("<html") && postBody.isNotBlank()) {
                            resStr = postBody
                            fetchSuccess = true
                            break
                        }
                    } catch (pe: Exception) {
                        Log.e("GoogleSheetsSync", "POST $act fallback error: ${pe.message}")
                    }
                }
            }

            if (resStr.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Respons dari Google Sheets kosong."))
            }

            // Deteksi jika Google Apps Script mengembalikan halaman HTML login / izin
            if (resStr.startsWith("<!DOCTYPE") || resStr.startsWith("<html")) {
                val ex = extractAppsScriptException(resStr)
                if (ex != null) {
                    return@withContext Result.failure(
                        IllegalStateException("Google Apps Script Error: $ex\n\nSolusi: Buka Super Admin di aplikasi, salin kode Code.gs terbaru, lalu Deploy ulang di script.google.com.")
                    )
                }
                return@withContext Result.failure(
                    IllegalStateException("Web App mengembalikan halaman web/login, bukan data JSON.\nPastikan di Google Apps Script: Deploy > Manage deployments > Who has access dipilih 'Anyone' (Siapa saja).")
                )
            }

            val list = mutableListOf<Student>()
            val arr: JSONArray = when {
                resStr.startsWith("[") -> {
                    try { JSONArray(resStr) } catch (_: Exception) { JSONArray() }
                }
                resStr.startsWith("{") -> {
                    try {
                        val json = JSONObject(resStr)
                        val status = json.optString("status", "")
                        if (status.equals("error", ignoreCase = true)) {
                            val msg = json.optString("message", "Gagal memuat data dari spreadsheet")
                            if (msg.contains("Aksi tidak dikenal", ignoreCase = true)) {
                                return@withContext Result.failure(
                                    IllegalStateException(
                                        "Script Google Apps Script di Spreadsheet Anda belum diperbarui ke versi terbaru.\n\n" +
                                        "Solusi:\n" +
                                        "1. Buka akun Super Admin di aplikasi ini\n" +
                                        "2. Masuk ke menu 'Google Sheets Setup' > Tab 'Kode Apps Script'\n" +
                                        "3. Klik 'Salin Kode' (Code.gs)\n" +
                                        "4. Buka script.google.com, ganti isi Code.gs dan klik 'Kelola Deployment > Versi Baru > Simpan'.\n" +
                                        "Setelah itu data siswa dan akun Users akan langsung terhubung secara otomatis!"
                                    )
                                )
                            }
                            return@withContext Result.failure(IllegalStateException(msg))
                        }
                        json.optJSONArray("students")
                            ?: json.optJSONArray("data")
                            ?: json.optJSONArray("siswa")
                            ?: json.optJSONArray("santri")
                            ?: json.optJSONArray("rows")
                            ?: json.optJSONArray("result")
                            ?: JSONArray()
                    } catch (_: Exception) {
                        JSONArray()
                    }
                }
                else -> JSONArray()
            }

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue

                // Cek variasi key untuk ID
                var rawId = obj.optString("id", "").trim()
                if (rawId.isBlank()) rawId = obj.optString("ID", "").trim()
                if (rawId.isBlank()) rawId = obj.optString("idSantri", "").trim()
                if (rawId.isBlank()) rawId = obj.optString("nis", "").trim()
                if (rawId.isBlank()) rawId = obj.optString("NIS", "").trim()
                if (rawId.isBlank()) rawId = obj.optString("noInduk", "").trim()
                if (rawId.isBlank()) rawId = obj.optString("no_induk", "").trim()
                if (rawId.isBlank()) rawId = obj.optString("nomor_induk", "").trim()
                if (rawId.isBlank()) rawId = obj.optString("barcode", "").trim()

                // Cek variasi key untuk Nama
                var rawName = obj.optString("name", "").trim()
                if (rawName.isBlank()) rawName = obj.optString("nama", "").trim()
                if (rawName.isBlank()) rawName = obj.optString("namaSantri", "").trim()
                if (rawName.isBlank()) rawName = obj.optString("nama_santri", "").trim()
                if (rawName.isBlank()) rawName = obj.optString("namaSiswa", "").trim()
                if (rawName.isBlank()) rawName = obj.optString("nama_siswa", "").trim()
                if (rawName.isBlank()) rawName = obj.optString("Nama", "").trim()
                if (rawName.isBlank()) rawName = obj.optString("Nama Siswa", "").trim()

                // Cek variasi key untuk NISN
                var rawNisn = obj.optString("nisn", "").trim()
                if (rawNisn.isBlank()) rawNisn = obj.optString("NISN", "").trim()
                if (rawNisn.isBlank()) rawNisn = obj.optString("no_nisn", "").trim()
                if (rawNisn.isBlank()) rawNisn = obj.optString("nomor_nisn", "").trim()
                if (rawNisn.isBlank()) rawNisn = obj.optString("password", "").trim()

                // Lewati jika baris ini adalah header tabel yang terbaca tidak sengaja
                if (rawId.equals("id", ignoreCase = true) || rawId.equals("nis", ignoreCase = true) ||
                    rawName.equals("nama", ignoreCase = true) || rawName.equals("nama siswa", ignoreCase = true) ||
                    rawName.equals("nama santri", ignoreCase = true)
                ) {
                    continue
                }

                // Abaikan baris kosong tanpa identitas
                if (rawId.isBlank() && rawName.isBlank() && rawNisn.isBlank()) {
                    continue
                }

                val finalId = when {
                    rawId.isNotBlank() -> rawId
                    rawNisn.isNotBlank() -> rawNisn
                    else -> "SAN-${i + 1}"
                }

                val finalName = if (rawName.isNotBlank()) rawName else "Santri $finalId"
                val finalNisn = if (rawNisn.isNotBlank()) rawNisn else finalId

                var gender = obj.optString("gender", "").trim()
                if (gender.isBlank()) gender = obj.optString("jenisKelamin", "").trim()
                if (gender.isBlank()) gender = obj.optString("jenis_kelamin", "").trim()
                if (gender.isBlank()) gender = obj.optString("Jenis Kelamin", "").trim()
                if (gender.isBlank()) gender = obj.optString("jk", "").trim()
                if (gender.isBlank()) gender = obj.optString("JK", "").trim()
                if (gender.isBlank()) gender = "Laki-laki"

                var className = obj.optString("className", "").trim()
                if (className.isBlank()) className = obj.optString("kelas", "").trim()
                if (className.isBlank()) className = obj.optString("Kelas", "").trim()
                if (className.isBlank()) className = obj.optString("rombel", "").trim()
                if (className.isBlank()) className = "-"

                var unit = obj.optString("unitPendidikan", "").trim()
                if (unit.isBlank()) unit = obj.optString("unit", "").trim()
                if (unit.isBlank()) unit = obj.optString("Unit Pendidikan", "").trim()
                if (unit.isBlank()) unit = obj.optString("Unit", "").trim()
                if (unit.isBlank()) unit = obj.optString("jenjang", "").trim()
                if (unit.isBlank()) unit = "MTs"

                var asrama = obj.optString("dormitory", "").trim()
                if (asrama.isBlank()) asrama = obj.optString("asrama", "").trim()
                if (asrama.isBlank()) asrama = obj.optString("Nama Asrama", "").trim()
                if (asrama.isBlank()) asrama = obj.optString("kobong", "").trim()
                if (asrama.isBlank()) asrama = "-"

                var ortu = obj.optString("parentName", "").trim()
                if (ortu.isBlank()) ortu = obj.optString("wali", "").trim()
                if (ortu.isBlank()) ortu = obj.optString("nama_ortu", "").trim()
                if (ortu.isBlank()) ortu = obj.optString("Nama Orang Tua", "").trim()
                if (ortu.isBlank()) ortu = obj.optString("Nama Ortu", "").trim()
                if (ortu.isBlank()) ortu = obj.optString("ortu", "").trim()
                if (ortu.isBlank()) ortu = "-"

                var hp = obj.optString("parentPhone", "").trim()
                if (hp.isBlank()) hp = obj.optString("noHp", "").trim()
                if (hp.isBlank()) hp = obj.optString("no_hp", "").trim()
                if (hp.isBlank()) hp = obj.optString("noWa", "").trim()
                if (hp.isBlank()) hp = obj.optString("no_wa", "").trim()
                if (hp.isBlank()) hp = obj.optString("No WA Ortu", "").trim()
                if (hp.isBlank()) hp = obj.optString("No HP Orang Tua", "").trim()

                var status = obj.optString("status", "").trim()
                if (status.isBlank()) status = obj.optString("Status", "").trim()
                if (status.isBlank()) status = "Siswa"

                list.add(
                    Student(
                        id = finalId,
                        nisn = finalNisn,
                        name = finalName,
                        gender = gender,
                        className = className,
                        unitPendidikan = unit,
                        dormitory = asrama,
                        parentName = ortu,
                        parentPhone = hp,
                        status = status
                    )
                )
            }

            if (list.isEmpty()) {
                Result.failure(IllegalStateException("Spreadsheet terhubung, namun tidak ada baris data santri yang terbaca. Pastikan sheet santri berisi kolom ID, NISN, dan Nama."))
            } else {
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginOnline(username: String, password: String): Result<UserAccount> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl()
        if (scriptUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("URL Google Sheets belum diatur"))
        }

        try {
            val encU = java.net.URLEncoder.encode(username.trim(), "UTF-8")
            val encP = java.net.URLEncoder.encode(password.trim(), "UTF-8")
            val separator = if (scriptUrl.contains("?")) "&" else "?"
            val getUrl = "$scriptUrl${separator}action=login&username=$encU&password=$encP&t=${System.currentTimeMillis()}"

            var resStr = ""
            var fetchSuccess = false

            try {
                val request = Request.Builder().url(getUrl).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()?.trim() ?: ""
                if (response.isSuccessful && !body.startsWith("<!DOCTYPE") && !body.startsWith("<html") && body.isNotBlank()) {
                    resStr = body
                    fetchSuccess = true
                }
            } catch (_: Exception) {}

            if (!fetchSuccess) {
                try {
                    val payload = JSONObject().apply {
                        put("action", "login")
                        put("username", username.trim())
                        put("password", password.trim())
                    }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val postReq = Request.Builder().url(getUrl).post(payload).build()
                    val postRes = client.newCall(postReq).execute()
                    val postBody = postRes.body?.string()?.trim() ?: ""
                    if (postRes.isSuccessful && !postBody.startsWith("<!DOCTYPE") && !postBody.startsWith("<html") && postBody.isNotBlank()) {
                        resStr = postBody
                        fetchSuccess = true
                    }
                } catch (_: Exception) {}
            }

            if (resStr.isBlank() || resStr.startsWith("<!DOCTYPE") || resStr.startsWith("<html")) {
                val ex = extractAppsScriptException(resStr)
                val msg = if (ex != null) "Apps Script Error: $ex" else "Gagal menghubungi server database"
                return@withContext Result.failure(IllegalStateException(msg))
            }

            val json = JSONObject(resStr)
            val status = json.optString("status", "")
            if (status.equals("success", ignoreCase = true)) {
                val userObj = json.optJSONObject("user") ?: json
                val roleStr = json.optString("role", userObj.optString("role", "")).uppercase()
                val role = when {
                    roleStr.contains("SUPER") -> UserRole.SUPER_ADMIN
                    roleStr.contains("ADMIN") -> UserRole.ADMIN
                    roleStr.contains("GURU") -> UserRole.GURU
                    else -> UserRole.WALI_SANTRI
                }
                val user = UserAccount(
                    username = userObj.optString("username", username.trim()),
                    password = password.trim(),
                    displayName = userObj.optString("displayName", userObj.optString("name", username.trim())),
                    role = role,
                    unitPendidikan = userObj.optString("unitPendidikan", userObj.optString("unit", null)).takeIf { !it.isNullOrBlank() },
                    studentId = userObj.optString("studentId", if (role == UserRole.WALI_SANTRI) username.trim() else null).takeIf { !it.isNullOrBlank() }
                )
                Result.success(user)
            } else {
                val msg = json.optString("message", "Username atau Password salah")
                Result.failure(IllegalStateException(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUsersFromSheets(): Result<List<UserAccount>> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl()
        if (scriptUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("URL Google Sheets belum diatur"))
        }

        try {
            val separator = if (scriptUrl.contains("?")) "&" else "?"
            val userActions = listOf("getUsers", "get_users")
            var resStr = ""
            var fetchSuccess = false

            for (act in userActions) {
                if (fetchSuccess) break
                val getUrl = "$scriptUrl${separator}action=$act&t=${System.currentTimeMillis()}"

                try {
                    val request = Request.Builder().url(getUrl).get().build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()?.trim() ?: ""
                    if (response.isSuccessful && !body.startsWith("<!DOCTYPE") && !body.startsWith("<html") && body.isNotBlank()) {
                        resStr = body
                        fetchSuccess = true
                        break
                    }
                } catch (_: Exception) {}

                if (!fetchSuccess) {
                    try {
                        val payload = JSONObject().apply {
                            put("action", act)
                        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                        val postReq = Request.Builder().url(getUrl).post(payload).build()
                        val postRes = client.newCall(postReq).execute()
                        val postBody = postRes.body?.string()?.trim() ?: ""
                        if (postRes.isSuccessful && !postBody.startsWith("<!DOCTYPE") && !postBody.startsWith("<html") && postBody.isNotBlank()) {
                            resStr = postBody
                            fetchSuccess = true
                            break
                        }
                    } catch (_: Exception) {}
                }
            }

            if (resStr.isBlank() || resStr.startsWith("<!DOCTYPE") || resStr.startsWith("<html")) {
                val ex = extractAppsScriptException(resStr)
                val msg = if (ex != null) "Apps Script Error: $ex" else "Gagal memuat Users dari Sheets"
                return@withContext Result.failure(IllegalStateException(msg))
            }

            val json = JSONObject(resStr)
            val status = json.optString("status", "")
            if (status.equals("success", ignoreCase = true) || json.has("users")) {
                val arr = json.optJSONArray("users") ?: JSONArray()
                val list = mutableListOf<UserAccount>()
                for (i in 0 until arr.length()) {
    val obj = arr.getJSONObject(i)
    
    // Pengecekan variasi nama kolom untuk Email / ID Guru / Username
    var uName = obj.optString("username", "").trim()
    if (uName.isBlank()) uName = obj.optString("Username", "").trim()
    if (uName.isBlank()) uName = obj.optString("email", "").trim()
    if (uName.isBlank()) uName = obj.optString("Email", "").trim()
    if (uName.isBlank()) uName = obj.optString("id", "").trim()
    if (uName.isBlank()) uName = obj.optString("ID", "").trim()
    if (uName.isBlank()) uName = obj.optString("nip", "").trim()
    if (uName.isBlank()) uName = obj.optString("NIP", "").trim()
    if (uName.isBlank()) uName = obj.optString("user", "").trim()

    if (uName.isNotBlank()) {
        val rStr = obj.optString("role", obj.optString("Role", "Guru")).uppercase()
        val role = when {
            rStr.contains("SUPER") -> UserRole.SUPER_ADMIN
            rStr.contains("ADMIN") -> UserRole.ADMIN
            else -> UserRole.GURU
        }

        var dName = obj.optString("name", "").trim()
        if (dName.isBlank()) dName = obj.optString("Nama", "").trim()
        if (dName.isBlank()) dName = obj.optString("nama", "").trim()
        if (dName.isBlank()) dName = obj.optString("Nama Lengkap", "").trim()
        if (dName.isBlank()) dName = uName

        var pWord = obj.optString("password", "").trim()
        if (pWord.isBlank()) pWord = obj.optString("Password", "").trim()
        if (pWord.isBlank()) pWord = obj.optString("pass", "").trim()
        if (pWord.isBlank()) pWord = "123"

        var unit = obj.optString("unitPendidikan", "").trim()
        if (unit.isBlank()) unit = obj.optString("unit", "").trim()
        if (unit.isBlank()) unit = obj.optString("Unit Pendidikan", "").trim()
        if (unit.isBlank()) unit = if (role == UserRole.SUPER_ADMIN) "Semua" else "MTs"

        list.add(
            UserAccount(
                username = uName,
                password = pWord,
                displayName = dName,
                role = role,
                unitPendidikan = unit
            )
        )
    }
}
                Result.success(list)
            } else {
                Result.failure(IllegalStateException(json.optString("message", "Gagal memuat Users dari Sheets")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncAttendance(record: AttendanceRecord): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl()
        if (scriptUrl.isBlank() || !scriptUrl.startsWith("http")) {
            return@withContext Pair(false, "URL Google Apps Script belum dikonfigurasi. Masukkan URL deployment Google Sheets.")
        }

        try {
            // Encode parameters for redirect resilience
            val encId = java.net.URLEncoder.encode(record.studentId, "UTF-8")
            val encName = java.net.URLEncoder.encode(record.studentName, "UTF-8")
            val encClass = java.net.URLEncoder.encode(record.className, "UTF-8")
            val encUnit = java.net.URLEncoder.encode(record.unitPendidikan, "UTF-8")
            val encDorm = java.net.URLEncoder.encode(record.dormitory, "UTF-8")
            val encSession = java.net.URLEncoder.encode(record.sessionName, "UTF-8")
            val encDate = java.net.URLEncoder.encode(record.date, "UTF-8")
            val encTime = java.net.URLEncoder.encode(record.time, "UTF-8")
            val encStatus = java.net.URLEncoder.encode(record.status, "UTF-8")
            val encBy = java.net.URLEncoder.encode(record.scannedBy, "UTF-8")
            val encNotes = java.net.URLEncoder.encode(record.notes, "UTF-8")

            val sep = if (scriptUrl.contains("?")) "&" else "?"
            val fullUrl = "$scriptUrl${sep}action=record_attendance&id=$encId&studentName=$encName&className=$encClass&unitPendidikan=$encUnit&dormitory=$encDorm&session=$encSession&date=$encDate&time=$encTime&status=$encStatus&scannedBy=$encBy&notes=$encNotes"

            val json = JSONObject().apply {
                put("action", "record_attendance")
                put("id", record.studentId)
                put("studentName", record.studentName)
                put("className", record.className)
                put("unitPendidikan", record.unitPendidikan)
                put("dormitory", record.dormitory)
                put("session", record.sessionName)
                put("date", record.date)
                put("time", record.time)
                put("status", record.status)
                put("scannedBy", record.scannedBy)
                put("notes", record.notes)
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(fullUrl)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val resStr = response.body?.string() ?: ""

            if (response.isSuccessful) {
                try {
                    val resJson = JSONObject(resStr)
                    val status = resJson.optString("status", "")
                    if (status.equals("success", ignoreCase = true) || resJson.optBoolean("success", false)) {
                        Pair(true, "Tersinkron ke Google Sheets")
                    } else {
                        val msg = resJson.optString("message", resStr)
                        Pair(false, msg)
                    }
                } catch (_: Exception) {
                    Pair(true, "Tersimpan di Google Sheets")
                }
            } else {
                Pair(false, "HTTP ${response.code}: $resStr")
            }
        } catch (e: Exception) {
            Pair(false, "Koneksi bermasalah: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        const val KEY_SCRIPT_URL = "google_apps_script_url"
        const val DEFAULT_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbwHswyueVqCsXmSwQnEWRzo39BxabGXeA5j4i1phEwUPz2br6dVhChzasLOHqpRxm57hA/exec"
    }
}
