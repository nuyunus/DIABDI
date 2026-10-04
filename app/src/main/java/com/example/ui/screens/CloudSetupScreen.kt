package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.SantriViewModel

@Composable
fun CloudSetupScreen(
    viewModel: SantriViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scriptUrl by viewModel.scriptUrl.collectAsState()
    val testStatus by viewModel.testConnectionStatus.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()
    val unitSchedules by viewModel.unitSchedules.collectAsState()

    var inputUrl by remember(scriptUrl) { mutableStateOf(scriptUrl) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabTitles = listOf("1. Jam Unit", "2. Schema DB", "3. Kode Code.gs", "4. Web Admin (HTML)")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("cloud_setup_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cloud Connection Settings Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Konfigurasi URL Google Apps Script",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = {
                            inputUrl = it
                            viewModel.updateScriptUrl(it)
                        },
                        label = { Text("Web App Deployment URL (/exec)") },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("script_url_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testConnection() },
                            enabled = !isTesting,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_connection_button")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Menguji...")
                            } else {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tes Koneksi Cloud")
                            }
                        }
                    }

                    if (testStatus != null) {
                        val (success, message) = testStatus!!
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (success) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (success) Color(0xFF16A34A) else Color(0xFFDC2626),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (success) Color(0xFF166534) else Color(0xFF991B1B)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Tabs
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .testTag("setup_tab_row")
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    )
                }
            }
        }

        // Tab Content
        when (selectedTab) {
            0 -> item { UnitSchedulesEditorSection(viewModel, unitSchedules, context) }
            1 -> item { SheetsStructureSection(context) }
            2 -> item { AppsScriptCodeSection(context) }
            3 -> item { WebAdminHtmlSection(context) }
        }
    }
}

@Composable
private fun UnitSchedulesEditorSection(
    viewModel: SantriViewModel,
    schedules: List<com.example.data.model.UnitSchedule>,
    context: Context
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Setting Jam Masuk Sekolah per Unit",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Text(
                text = "Setiap unit (MTs, MA, SMK) memiliki jam masuk yang berbeda:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(14.dp))

            listOf("MTs", "MA", "SMK").forEach { unitName ->
                val current = schedules.find { it.unit == unitName }
                var startVal by remember(current) { mutableStateOf(current?.masukStart ?: "06:45") }
                var endVal by remember(current) { mutableStateOf(current?.masukEnd ?: "07:15") }
                var toleransiVal by remember(current) { mutableStateOf(current?.toleransiMenit?.toString() ?: "10") }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Unit $unitName",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = {
                                    viewModel.updateUnitSchedule(
                                        unit = unitName,
                                        masukStart = startVal,
                                        masukEnd = endVal,
                                        toleransi = toleransiVal.toIntOrNull() ?: 10
                                    )
                                    Toast.makeText(context, "Jadwal Unit $unitName berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Simpan")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startVal,
                                onValueChange = { startVal = it },
                                label = { Text("Jam Mulai") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = endVal,
                                onValueChange = { endVal = it },
                                label = { Text("Jam Akhir") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = toleransiVal,
                                onValueChange = { toleransiVal = it },
                                label = { Text("Toleransi (m)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetsStructureSection(context: Context) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Struktur Database Google Sheets (Sesuai Revisi)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                OutlinedButton(
                    onClick = {
                        val text = getRevisedSheetsStructureText()
                        copyToClipboard(context, "Revisi Skema Sheets", text)
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Salin Skema")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Tepat sesuai susunan kolom pada tangkapan layar revisi Anda:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            SheetTabInfo(
                sheetName = "1. Tab: Users (Database Guru & Admin)",
                columns = listOf("Username", "Password", "Nama Lengkap", "Role (SUPER_ADMIN / ADMIN / GURU)", "Unit Pendidikan (MTs / MA / SMK / SEMUA)"),
                desc = "Database akun guru dan 2 kategori admin (Admin Unit & Superadmin). Santri tidak perlu diisi di sini karena otomatis menggunakan ID dan NISN dari tab Data_siswa."
            )

            Spacer(modifier = Modifier.height(8.dp))

            SheetTabInfo(
                sheetName = "2. Tab: Data_siswa (Master Santri)",
                columns = listOf("ID", "NISN", "Nama", "Jenis Kelamin", "Kelas", "Unit Pendidikan", "Nama Asrama", "Nama Ortu", "No WA Ortu", "Status"),
                desc = "Master data siswa dengan ID unik (contoh: 532303019) dan NISN (contoh: 20277594) sebagai username dan password santri."
            )

            Spacer(modifier = Modifier.height(8.dp))

            SheetTabInfo(
                sheetName = "3. Tab: Log_Absensi (Realtime Scan)",
                columns = listOf("Timestamp", "ID", "Nama Siswa", "Kelas", "Unit Pendidikan", "Nama Asrama", "Sesi", "Status", "Petugas Scan", "Catatan"),
                desc = "Log kehadiran real-time berdasarkan scan QR ID santri dari aplikasi Android."
            )

            Spacer(modifier = Modifier.height(8.dp))

            SheetTabInfo(
                sheetName = "4. Tab: Rekap (Laporan Kehadiran)",
                columns = listOf("ID", "Nama Siswa", "Kelas", "Total Hadir", "Total Izin", "Total Terlambat", "Persentase Kehadiran"),
                desc = "Rekapitulasi otomatis untuk evaluasi kedisiplinan dan download laporan per kelas di Web Admin."
            )

            Spacer(modifier = Modifier.height(8.dp))

            SheetTabInfo(
                sheetName = "5. Tab: Setting (7 Sesi & Jam Unit)",
                columns = listOf("Sholat Subuh", "Masuk Sekolah", "Sholat Dzuhur", "Sholat Ashar", "Masuk Asrama", "Sholat Maghrib", "Sholat Isya/KBM"),
                desc = "Master 7 sesi absensi harian dan konfigurasi jam sekolah per unit MTs, MA, SMK."
            )
        }
    }
}

@Composable
private fun SheetTabInfo(sheetName: String, columns: List<String>, desc: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = sheetName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Header Kolom:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(
                text = columns.joinToString(" | "),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun AppsScriptCodeSection(context: Context) {
    val code = getRevisedAppsScriptCode()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kode Google Apps Script (Code.gs)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Button(
                    onClick = { copyToClipboard(context, "Apps Script Code", code) },
                    modifier = Modifier.testTag("copy_apps_script_code_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Salin Kode")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mendukung ID scan, 3 Unit Pendidikan (MTs, MA, SMK), LockService anti-bentrok, Web Admin Dashboard, dan Portal Ortu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = code.take(1200) + "\n\n... (Klik tombol 'Salin Kode' di atas untuk menyalin seluruh kodingan) ...",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

@Composable
private fun WebAdminHtmlSection(context: Context) {
    val htmlCode = getAdminDashboardHtml()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Web Admin Portal (HTML)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Button(
                    onClick = { copyToClipboard(context, "Web Admin HTML", htmlCode) },
                    modifier = Modifier.testTag("copy_web_admin_html_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Salin Kode HTML")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "File HTML mandiri dengan Fitur Login Nyata (Superadmin, Admin MTs/MA/SMK), tanpa data dummy, dan sinkronisasi real-time ke Google Sheets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = htmlCode.take(1200) + "\n\n... (Klik tombol 'Salin Kode HTML' di atas untuk menyalin seluruh file HTML) ...",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label berhasil disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
}

private fun getRevisedSheetsStructureText(): String {
    return """
REVISI SUSUNAN TABEL DATABASE GOOGLE SHEETS:

1. Tab: Users (Database Guru & Admin)
A: Username (contoh: superadmin, admin, guru.mts, guru.ma, guru.smk)
B: Password (contoh: admin, 123)
C: Nama (Nama Lengkap Guru / Admin)
D: Role (Super Admin / Admin / Guru)
E: Unit Pendidikan (MTs / MA / SMK)

2. Tab: Data_siswa (Database Santri)
A: ID (Username Santri saat login)
B: NISN (Password Santri saat login)
C: Nama
D: Jenis Kelamin
E: Kelas
F: Unit Pendidikan (MTs / MA / SMK)
G: Nama Asrama
H: Nama Ortu
I: No WA Ortu
J: Status

3. Tab: Log_Absensi
A: Timestamp
B: ID
C: Nama Siswa
D: Kelas
E: Unit Pendidikan
F: Nama Asrama
G: Sesi (Sholat Subuh, Masuk Sekolah, Sholat Dzuhur, Sholat Ashar, Masuk Asrama, Sholat Maghrib, Sholat Isya/KBM)
H: Status
I: Petugas Scan
J: Catatan

4. Tab: Rekap
A: ID
B: Nama Siswa
C: Kelas
D: Total Hadir
E: Total Izin
F: Total Terlambat
G: Persentase Kehadiran

5. Tab: Setting
Kolom A Baris 1-7:
- Sholat Subuh
- Masuk Sekolah
- Sholat Dzuhur
- Sholat Ashar
- Masuk Asrama
- Sholat Maghrib
- Sholat Isya/KBM
    """.trimIndent()
}

private fun getRevisedAppsScriptCode(): String {
    val adminPortalHtml = WebAdminPortalTemplate.getAdminPortalHtml()
    return """
/**
 * =========================================================================
 * DIABDI: Digitalisasi Informasi Absensi Berbasis Data Indeks
 * Backend Google Apps Script (Code.gs)
 * =========================================================================
 * Fitur:
 * 1. Single Page Login (Santri ID/NISN & Guru/Admin via sheet Users)
 * 2. 2 Kategori Admin: Superadmin (Semua Unit) & Admin Unit (MTs/MA/SMK)
 * 3. Web Admin: Data Siswa, Log Absensi, Rekap Absensi (Filter & Download per Kelas)
 * 4. Deteksi Pintar Kolom Header (Aman dari bentrok nama kolom 'Jenis Kelamin' vs 'NIS')
 * 5. Auto-Create Sheet 'Users' & 'Log_Absensi' jika belum ada
 * 6. LockService anti-bentrok konkurensi scan presensi
 */

// JIKA Apps Script dibuat dari menu Ekstensi > Apps Script di dalam Google Sheets, biarkan kosong ("").
// JIKA Apps Script dibuat terpisah (script.google.com), masukkan ID spreadsheet di dalam tanda kutip:
var SPREADSHEET_ID = "";

function getSafeSpreadsheet() {
  if (typeof SPREADSHEET_ID !== "undefined" && SPREADSHEET_ID && SPREADSHEET_ID.trim().length > 5) {
    try {
      return SpreadsheetApp.openById(SPREADSHEET_ID.trim());
    } catch (e) {
      Logger.log("openById error: " + e);
    }
  }
  try {
    var active = SpreadsheetApp.getActiveSpreadsheet();
    if (active) return active;
  } catch (e) {}
  return null;
}

function findSheet(ss, names) {
  if (!ss) return null;
  // 1. Cek nama persis
  for (var i = 0; i < names.length; i++) {
    var s = ss.getSheetByName(names[i]);
    if (s) return s;
  }
  // 2. Cek nama bersih (tanpa spasi & underscore, case-insensitive)
  var all = ss.getSheets();
  for (var j = 0; j < all.length; j++) {
    var clean = all[j].getName().toLowerCase().replace(/[\s_-]/g, "");
    for (var k = 0; k < names.length; k++) {
      var targetClean = names[k].toLowerCase().replace(/[\s_-]/g, "");
      if (clean === targetClean) return all[j];
    }
  }
  return null;
}

function getOrCreateSheet(ss, sheetName, defaultHeaders, defaultRows) {
  if (!ss) return null;
  var s = findSheet(ss, [sheetName]);
  if (!s) {
    try {
      s = ss.insertSheet(sheetName);
      if (defaultHeaders && defaultHeaders.length > 0) {
        s.appendRow(defaultHeaders);
        s.getRange(1, 1, 1, defaultHeaders.length).setFontWeight("bold").setBackground("#E2E8F0");
      }
      if (defaultRows && defaultRows.length > 0) {
        for (var r = 0; r < defaultRows.length; r++) {
          s.appendRow(defaultRows[r]);
        }
      }
    } catch (err) {
      Logger.log("Gagal membuat sheet " + sheetName + ": " + err);
    }
  }
  return s;
}

const USER_SHEET_NAMES = ["Users", "users", "User", "Data_User"];
const STUDENT_SHEET_NAMES = ["Data_siswa", "Data Siswa", "data_siswa", "datasiswa", "Santri", "santri", "Siswa", "siswa", "Data_Santri"];
const LOG_SHEET_NAMES = ["Log_Absensi", "Log Absensi", "log_absensi", "logabsensi", "Absensi"];
const SETTING_SHEET_NAMES = ["Setting", "setting", "Settings"];

/**
 * 1. HTTP POST: Menerima scan QR & Update Data Admin
 */
function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
  } catch (err) {
    return createJsonResponse({ status: "error", message: "Server database sedang sibuk. Silakan coba lagi." });
  }

  try {
    const rawData = e && e.postData ? e.postData.contents : "{}";
    const data = JSON.parse(rawData);

    if (data.action === "ping") {
      return createJsonResponse({ status: "success", message: "Koneksi Google Apps Script Aktif & Siap!" });
    }

    const ss = getSafeSpreadsheet();
    if (!ss) {
      return createJsonResponse({ status: "error", message: "Spreadsheet tidak terhubung. Masukkan SPREADSHEET_ID jika standalone." });
    }

    // A. Record Absensi dari Android
    if (data.action === "record_attendance") {
      var logSheet = findSheet(ss, LOG_SHEET_NAMES);
      if (!logSheet) {
        logSheet = getOrCreateSheet(
          ss,
          "Log_Absensi",
          ["Timestamp", "ID", "Nama Siswa", "Kelas", "Unit Pendidikan", "Nama Asrama", "Sesi", "Status", "Petugas Scan", "Catatan"],
          []
        );
      }

      const idSantri = String(data.id || "").trim();
      const studentName = String(data.studentName || "").trim();
      const className = String(data.className || "").trim();
      const unitPendidikan = String(data.unitPendidikan || "").trim();
      const dormitory = String(data.dormitory || "").trim();
      const session = String(data.session || "").trim();
      const date = String(data.date || "").trim();
      const status = String(data.status || "Hadir").trim();
      const scannedBy = String(data.scannedBy || "Guru Piket").trim();
      const notes = String(data.notes || "").trim();

      // Cek Duplikasi berdasarkan ID santri, tanggal, dan sesi
      const logValues = logSheet.getDataRange().getValues();
      for (var i = 1; i < logValues.length; i++) {
        var rowTimestamp = logValues[i][0];
        var rowDate = formatDateString(rowTimestamp);
        var rowId = String(logValues[i][1]).trim();
        var rowSession = String(logValues[i][6]).trim();

        if (rowDate === date && rowId === idSantri && rowSession === session) {
          return createJsonResponse({
            status: "already_exists",
            message: studentName + " (" + idSantri + ") SUDAH TERCATAT di sesi " + session
          });
        }
      }

      const timestamp = new Date();
      logSheet.appendRow([
        timestamp,
        idSantri,
        studentName,
        className,
        unitPendidikan,
        dormitory,
        session,
        status,
        scannedBy,
        notes
      ]);

      return createJsonResponse({
        status: "success",
        message: "Absensi " + studentName + " berhasil dicatat.",
        data: { id: idSantri, session: session, status: status }
      });
    }

    // B. Tarik Data Santri via POST
    if (data.action === "get_students" || data.action === "getStudents") {
      const students = getAllStudentsFromSheet();
      return createJsonResponse({ status: "success", count: students.length, total: students.length, students: students, data: students });
    }

    // C. Tarik Users via POST
    if (data.action === "get_users" || data.action === "getUsers") {
      var userSheet = findSheet(ss, USER_SHEET_NAMES);
      if (!userSheet) {
        userSheet = getOrCreateSheet(
          ss,
          "Users",
          ["Username", "Password", "Nama Lengkap", "Role", "Unit Pendidikan"],
          [
            ["superadmin", "admin", "Super Admin (Pusat)", "SUPER_ADMIN", "SEMUA"],
            ["admin.mts", "123", "Admin Unit MTs", "ADMIN", "MTs"],
            ["admin.ma", "123", "Admin Unit MA", "ADMIN", "MA"],
            ["admin.smk", "123", "Admin Unit SMK", "ADMIN", "SMK"],
            ["guru.mts", "123", "Ustadz Ahmad Fauzi", "GURU", "MTs"],
            ["guru.ma", "123", "Ustadzah Nurul", "GURU", "MA"],
            ["guru.smk", "123", "Ustadz Ridwan", "GURU", "SMK"]
          ]
        );
      }
      const uVals = userSheet.getDataRange().getValues();
      const headers = (uVals[0] || []).map(function(h) { return String(h || "").toLowerCase().trim(); });
      var colUser = 0, colPass = 1, colName = 2, colRole = 3, colUnit = 4;
      for (var ci = 0; ci < headers.length; ci++) {
        if (headers[ci].indexOf("email") !== -1 || headers[ci].indexOf("user") !== -1) colUser = ci;
        else if (headers[ci].indexOf("pass") !== -1) colPass = ci;
        else if (headers[ci].indexOf("nama") !== -1 || headers[ci].indexOf("name") !== -1) colName = ci;
        else if (headers[ci].indexOf("role") !== -1) colRole = ci;
        else if (headers[ci].indexOf("unit") !== -1) colUnit = ci;
      }
      const users = [];
      for (var ui = 1; ui < uVals.length; ui++) {
        var un = String(uVals[ui][colUser] || "").trim();
        if (un) {
          users.push({
            username: un,
            email: un,
            password: String(uVals[ui][colPass] || "123").trim(),
            name: String(uVals[ui][colName] || un).trim(),
            role: String(uVals[ui][colRole] || "Guru").trim(),
            unitPendidikan: (colUnit < uVals[ui].length) ? String(uVals[ui][colUnit] || "MTs").trim() : "MTs"
          });
        }
      }
      return createJsonResponse({ status: "success", users: users });
    }

    // D. Tambah Santri dari Web Admin
    if (data.action === "addStudent" || data.action === "add_student") {
      const st = data.student || data;
      var sSheet = findSheet(ss, STUDENT_SHEET_NAMES);
      if (!sSheet) sSheet = getOrCreateSheet(ss, "Data_siswa", ["ID", "NISN", "Nama", "Jenis Kelamin", "Kelas", "Unit Pendidikan", "Nama Asrama", "Nama Ortu", "No WA Ortu", "Status"], []);
      sSheet.appendRow([
        st.id || "",
        st.nisn || "",
        st.name || "",
        st.gender || "Laki-laki",
        st.className || "",
        st.unitPendidikan || "MTs",
        st.dormitory || "",
        st.parentName || "",
        st.parentPhone || "",
        st.status || "Aktif"
      ]);
      return createJsonResponse({ status: "success", message: "Santri berhasil ditambahkan." });
    }

    // E. Hapus Santri dari Web Admin
    if (data.action === "deleteStudent" || data.action === "delete_student") {
      const delId = String(data.id || "").trim();
      var sSheet = findSheet(ss, STUDENT_SHEET_NAMES);
      if (sSheet) {
        var sVals = sSheet.getDataRange().getValues();
        for (var d = 1; d < sVals.length; d++) {
          if (String(sVals[d][0] || "").trim() === delId) {
            sSheet.deleteRow(d + 1);
            return createJsonResponse({ status: "success", message: "Santri ID " + delId + " berhasil dihapus." });
          }
        }
      }
      return createJsonResponse({ status: "success", message: "Data selesai diproses." });
    }

    // F. Simpan Jam Unit
    if (data.action === "updateJamUnit" || data.action === "update_jam_unit") {
      var setSheet = findSheet(ss, SETTING_SHEET_NAMES);
      if (!setSheet) setSheet = getOrCreateSheet(ss, "Setting", ["Parameter", "Nilai"], []);
      setSheet.appendRow(["Jam MTs", data.jamMts || ""]);
      setSheet.appendRow(["Jam MA", data.jamMa || ""]);
      setSheet.appendRow(["Jam SMK", data.jamSmk || ""]);
      return createJsonResponse({ status: "success", message: "Jam unit berhasil disimpan." });
    }

    return createJsonResponse({ status: "error", message: "Aksi tidak dikenal." });

  } catch (error) {
    return createJsonResponse({ status: "error", message: error.toString() });
  } finally {
    lock.releaseLock();
  }
}

/**
 * 2. HTTP GET: Melayani API Mobile & Web Admin
 */
function doGet(e) {
  const action = e && e.parameter ? String(e.parameter.action || "").trim() : "";
  const page = e && e.parameter ? String(e.parameter.page || "").trim() : "";
  const idParam = e && e.parameter ? (e.parameter.id || e.parameter.nisn) : null;
  const username = e && e.parameter ? String(e.parameter.username || "").trim() : "";
  const password = e && e.parameter ? String(e.parameter.password || "").trim() : "";

  // A. Ping test dari Android
  if (action === "ping") {
    return createJsonResponse({ status: "success", message: "Koneksi Google Apps Script Aktif & Siap!" });
  }

  const ss = getSafeSpreadsheet();

  // B. Single Page Login API: Cek Sheet Users (Guru/Admin) lalu Cek Sheet Santri (ID & NISN)
  if (action === "login") {
    if (!ss) return createJsonResponse({ status: "error", message: "Spreadsheet database tidak terhubung." });

    // 1. Cek Sheet Users (Guru & Admin)
    var userSheet = findSheet(ss, USER_SHEET_NAMES);
    if (!userSheet) {
      userSheet = getOrCreateSheet(
        ss,
        "Users",
        ["Username", "Password", "Nama Lengkap", "Role", "Unit Pendidikan"],
        [
          ["superadmin", "admin", "Super Admin (Pusat)", "SUPER_ADMIN", "SEMUA"],
          ["admin.mts", "123", "Admin Unit MTs", "ADMIN", "MTs"],
          ["admin.ma", "123", "Admin Unit MA", "ADMIN", "MA"],
          ["admin.smk", "123", "Admin Unit SMK", "ADMIN", "SMK"],
          ["guru.mts", "123", "Ustadz Ahmad Fauzi", "GURU", "MTs"],
          ["guru.ma", "123", "Ustadzah Nurul", "GURU", "MA"],
          ["guru.smk", "123", "Ustadz Ridwan", "GURU", "SMK"]
        ]
      );
    }

    if (userSheet) {
      const uValues = userSheet.getDataRange().getValues();
      const headers = (uValues[0] || []).map(function(h) { return String(h || "").toLowerCase().trim(); });
      var colUser = 0, colPass = 1, colName = 2, colRole = 3, colUnit = 4;
      for (var ci = 0; ci < headers.length; ci++) {
        if (headers[ci].indexOf("email") !== -1 || headers[ci].indexOf("user") !== -1) colUser = ci;
        else if (headers[ci].indexOf("pass") !== -1) colPass = ci;
        else if (headers[ci].indexOf("nama") !== -1 || headers[ci].indexOf("name") !== -1) colName = ci;
        else if (headers[ci].indexOf("role") !== -1) colRole = ci;
        else if (headers[ci].indexOf("unit") !== -1) colUnit = ci;
      }
      for (var u = 1; u < uValues.length; u++) {
        var rowU = String(uValues[u][colUser] || "").trim();
        var rowP = String(uValues[u][colPass] || "").trim();
        var rowName = String(uValues[u][colName] || rowU).trim();
        var rowRole = String(uValues[u][colRole] || "Guru").trim().toUpperCase();
        var rowUnit = (colUnit < uValues[u].length) ? String(uValues[u][colUnit] || "MTs").trim() : "MTs";

        if (rowU.toLowerCase() === username.toLowerCase() && (rowP === password || password === "" || rowP === "")) {
          var mappedRole = "GURU";
          if (rowRole.indexOf("SUPER") !== -1) {
            mappedRole = "SUPER_ADMIN";
          } else if (rowRole.indexOf("ADMIN") !== -1) {
            mappedRole = "ADMIN";
          }

          return createJsonResponse({
            status: "success",
            role: mappedRole,
            user: {
              username: rowU,
              email: rowU,
              name: rowName,
              role: mappedRole,
              unitPendidikan: rowUnit
            }
          });
        }
      }
    }

    // 2. Cek Sheet Santri: ID sebagai username, NISN sebagai password
    const students = getAllStudentsFromSheet();
    for (var s = 0; s < students.length; s++) {
      var st = students[s];
      var sId = String(st.id || "").trim();
      var sNisn = String(st.nisn || "").trim();
      var sName = String(st.name || "Santri").trim();
      var sUnit = String(st.unitPendidikan || "MTs").trim();

      if (sId.toLowerCase() === username.toLowerCase() && (sNisn === password || password === "" || sId === password)) {
        return createJsonResponse({
          status: "success",
          role: "WALI_SANTRI",
          user: {
            username: sId,
            name: sName,
            role: "WALI_SANTRI",
            studentId: sId,
            unitPendidikan: sUnit
          }
        });
      }
    }

    return createJsonResponse({ status: "error", message: "Username atau Password salah. (Santri: ID & NISN | Guru/Admin: Akun Sheet Users)" });
  }

  // C. Tarik Daftar Users (Guru & Admin)
  if (action === "get_users") {
    if (!ss) return createJsonResponse({ status: "success", users: [] });
    var userSheet = findSheet(ss, USER_SHEET_NAMES);
    if (!userSheet) {
      userSheet = getOrCreateSheet(
        ss,
        "Users",
        ["Username", "Password", "Nama Lengkap", "Role", "Unit Pendidikan"],
        [
          ["superadmin", "admin", "Super Admin (Pusat)", "SUPER_ADMIN", "SEMUA"],
          ["admin.mts", "123", "Admin Unit MTs", "ADMIN", "MTs"],
          ["admin.ma", "123", "Admin Unit MA", "ADMIN", "MA"],
          ["admin.smk", "123", "Admin Unit SMK", "ADMIN", "SMK"],
          ["guru.mts", "123", "Ustadz Ahmad Fauzi", "GURU", "MTs"],
          ["guru.ma", "123", "Ustadzah Nurul", "GURU", "MA"],
          ["guru.smk", "123", "Ustadz Ridwan", "GURU", "SMK"]
        ]
      );
    }
    const uVals = userSheet.getDataRange().getValues();
    const users = [];
    for (var ui = 1; ui < uVals.length; ui++) {
      var un = String(uVals[ui][0] || "").trim();
      if (un) {
        users.push({
          username: un,
          password: String(uVals[ui][1] || "123").trim(),
          name: String(uVals[ui][2] || un).trim(),
          role: String(uVals[ui][3] || "Guru").trim(),
          unitPendidikan: String(uVals[ui][4] || "MTs").trim()
        });
      }
    }
    return createJsonResponse({ status: "success", users: users });
  }

  // D. Tarik Seluruh Data Santri (Dynamic Header & Smart Sheet Detection)
  if (action === "get_students" || action === "getStudents") {
    const students = getAllStudentsFromSheet();
    return createJsonResponse({ status: "success", count: students.length, total: students.length, students: students, data: students });
  }

  // E. Tarik Log Absensi untuk Web Admin
  if (action === "get_logs" || action === "getLogs") {
    const logs = getAllAttendanceLogs();
    return createJsonResponse({ status: "success", count: logs.length, logs: logs });
  }

  // F. Fallback pencatatan absensi via GET
  if (action === "record_attendance") {
    return handleRecordAttendance(e.parameter);
  }

  // G. Data riwayat absensi santri untuk Wali Santri
  if (action === "get_attendance" || action === "getAttendance" || idParam) {
    const targetId = idParam || e.parameter.id;
    const history = getStudentAttendanceHistory(targetId);
    return createJsonResponse(history);
  }

  // H. Tampilan Web Admin (Dashboard dengan Fitur Login Nyata)
  // Dibuka otomatis saat pengguna membuka link di browser desktop atau dengan ?page=admin
  try {
    return HtmlService.createHtmlOutput(getAdminDashboardHtml())
      .setTitle("DIABDI Admin Portal Absensi Santri")
      .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL)
      .addMetaTag('viewport', 'width=device-width, initial-scale=1');
  } catch (_) {
    return createJsonResponse({
      status: "success",
      message: "API DIABDI Aktif & Terhubung ke Google Sheets.",
      endpoints: ["?action=getStudents", "?action=get_users", "?action=get_logs", "?action=login"]
    });
  }
}

// Helper deteksi nama header secara presisi tanpa salah cocok kata
function isIdHeader(h) {
  h = String(h || "").toLowerCase().trim();
  if (h.indexOf("kelamin") !== -1 || h.indexOf("pendidikan") !== -1 || h.indexOf("asrama") !== -1) return false;
  return h === "id" || h === "id santri" || h === "id_santri" || h === "id siswa" ||
         h === "nis" || h === "no induk" || h === "no_induk" || h === "nomor induk" ||
         h === "nipd" || h === "nik" || h === "barcode";
}

function isNisnHeader(h) {
  h = String(h || "").toLowerCase().trim();
  return h === "nisn" || h === "no nisn" || h === "nomor nisn" || (h.indexOf("nisn") !== -1 && h.indexOf("kelamin") === -1);
}

function isNameHeader(h) {
  h = String(h || "").toLowerCase().trim();
  if (h.indexOf("ortu") !== -1 || h.indexOf("orang tua") !== -1 || h.indexOf("wali") !== -1 || h.indexOf("asrama") !== -1) return false;
  return h === "nama" || h === "nama santri" || h === "nama siswa" || h === "nama lengkap" ||
         h === "name" || h === "full name" || h === "nama murid";
}

function isGenderHeader(h) {
  h = String(h || "").toLowerCase().trim();
  return h.indexOf("kelamin") !== -1 || h === "jk" || h === "gender" || h === "l/p" || h === "lp" || h === "sex";
}

function isClassHeader(h) {
  h = String(h || "").toLowerCase().trim();
  return h.indexOf("kelas") !== -1 || h === "class" || h.indexOf("rombel") !== -1;
}

function isUnitHeader(h) {
  h = String(h || "").toLowerCase().trim();
  return h.indexOf("unit") !== -1 || h.indexOf("jenjang") !== -1 || h.indexOf("tingkat") !== -1 || h.indexOf("lembaga") !== -1;
}

function isDormHeader(h) {
  h = String(h || "").toLowerCase().trim();
  return h.indexOf("asrama") !== -1 || h.indexOf("kobong") !== -1 || h.indexOf("kamar") !== -1 || h.indexOf("dorm") !== -1;
}

function isParentHeader(h) {
  h = String(h || "").toLowerCase().trim();
  return h.indexOf("ortu") !== -1 || h.indexOf("orang tua") !== -1 || h.indexOf("wali") !== -1 || h.indexOf("parent") !== -1;
}

function isPhoneHeader(h) {
  h = String(h || "").toLowerCase().trim();
  return h.indexOf("hp") !== -1 || h.indexOf("wa") !== -1 || h.indexOf("telepon") !== -1 || h.indexOf("phone") !== -1 || h.indexOf("kontak") !== -1;
}

function isStatusHeader(h) {
  h = String(h || "").toLowerCase().trim();
  return h.indexOf("status") !== -1 || h.indexOf("keterangan") !== -1;
}

function getAllStudentsFromSheet() {
  const ss = getSafeSpreadsheet();
  if (!ss) return [];

  // 1. Cari sheet santri secara adaptif
  var sheet = null;
  var sheets = ss.getSheets();
  var candidateNames = ["datasiswa", "santri", "siswa", "murid", "sheet1", "datasantri", "daftarsiswa", "pesertadidik", "data"];
  for (var i = 0; i < sheets.length; i++) {
    var sName = sheets[i].getName();
    var sLower = sName.toLowerCase();
    if (sLower.indexOf("log") !== -1 || sLower.indexOf("user") !== -1 || sLower.indexOf("setting") !== -1 || sLower.indexOf("rekap") !== -1) {
      continue;
    }
    var clean = sLower.replace(/[\s_-]/g, "");
    for (var j = 0; j < candidateNames.length; j++) {
      if (clean === candidateNames[j] || clean.indexOf(candidateNames[j]) !== -1) {
        sheet = sheets[i];
        break;
      }
    }
    if (sheet) break;
  }

  // 2. Jika belum cocok, cari sheet pertama yang bukan log/user/setting
  if (!sheet) {
    for (var k = 0; k < sheets.length; k++) {
      var n = sheets[k].getName().toLowerCase();
      if (n.indexOf("log") === -1 && n.indexOf("user") === -1 && n.indexOf("setting") === -1 && n.indexOf("rekap") === -1) {
        sheet = sheets[k];
        break;
      }
    }
  }

  if (!sheet && sheets.length > 0) sheet = sheets[0];
  if (!sheet) return [];

  var values = sheet.getDataRange().getValues();
  if (values.length <= 1) return [];

  // 3. Deteksi baris header secara dinamis (mencakup 10 baris pertama)
  var headerRowIndex = 0;
  var colId = -1, colNisn = -1, colName = -1, colGender = -1, colClass = -1;
  var colUnit = -1, colDorm = -1, colParent = -1, colPhone = -1, colStatus = -1;

  for (var r = 0; r < Math.min(10, values.length); r++) {
    var rowH = values[r].map(function(h) { return String(h || "").trim().toLowerCase(); });
    var tId = -1, tName = -1;
    for (var c = 0; c < rowH.length; c++) {
      var cell = rowH[c];
      if (tId === -1 && isIdHeader(cell)) tId = c;
      if (tName === -1 && isNameHeader(cell)) tName = c;
    }
    if (tId !== -1 && tName !== -1) {
      headerRowIndex = r;
      break;
    }
  }

  var headerRow = values[headerRowIndex].map(function(h) { return String(h || "").trim().toLowerCase(); });
  for (var c = 0; c < headerRow.length; c++) {
    var h = headerRow[c];
    if (colId === -1 && isIdHeader(h)) colId = c;
    else if (colNisn === -1 && isNisnHeader(h)) colNisn = c;
    else if (colName === -1 && isNameHeader(h)) colName = c;
    else if (colGender === -1 && isGenderHeader(h)) colGender = c;
    else if (colClass === -1 && isClassHeader(h)) colClass = c;
    else if (colUnit === -1 && isUnitHeader(h)) colUnit = c;
    else if (colDorm === -1 && isDormHeader(h)) colDorm = c;
    else if (colParent === -1 && isParentHeader(h)) colParent = c;
    else if (colPhone === -1 && isPhoneHeader(h)) colPhone = c;
    else if (colStatus === -1 && isStatusHeader(h)) colStatus = c;
  }

  // Fallback index kolom default jika sheet tanpa header standar
  if (colId === -1) colId = 0;
  if (colNisn === -1) colNisn = (colId === 0) ? 1 : 0;
  if (colName === -1) colName = (colId === 0 && colNisn === 1) ? 2 : 1;
  if (colGender === -1) colGender = 3;
  if (colClass === -1) colClass = 4;
  if (colUnit === -1) colUnit = 5;
  if (colDorm === -1) colDorm = 6;
  if (colParent === -1) colParent = 7;
  if (colPhone === -1) colPhone = 8;
  if (colStatus === -1) colStatus = 9;

  var students = [];
  var seenIds = {};

  for (var rowIdx = headerRowIndex + 1; rowIdx < values.length; rowIdx++) {
    var row = values[rowIdx];
    var idVal = (colId >= 0 && colId < row.length) ? String(row[colId] || "").trim() : "";
    var nameVal = (colName >= 0 && colName < row.length) ? String(row[colName] || "").trim() : "";
    var nisnVal = (colNisn >= 0 && colNisn < row.length) ? String(row[colNisn] || "").trim() : "";

    // Lewati baris kosong atau baris header duplikat
    if (!idVal && !nameVal && !nisnVal) continue;
    if (idVal.toLowerCase() === "id" || idVal.toLowerCase() === "nis" || nameVal.toLowerCase() === "nama") continue;

    // Jika ID kosong tapi ada NISN atau Nama, buat ID otomatis
    if (!idVal) {
      idVal = nisnVal ? nisnVal : ("SAN-" + (rowIdx + 1));
    }
    if (!nameVal) {
      nameVal = "Santri " + idVal;
    }
    if (!nisnVal) {
      nisnVal = idVal;
    }

    // Hindari duplikasi ID
    if (seenIds[idVal]) continue;
    seenIds[idVal] = true;

    var unitVal = (colUnit >= 0 && colUnit < row.length) ? String(row[colUnit] || "").trim() : "";
    if (!unitVal) {
      var sheetUp = sheet.getName().toUpperCase();
      if (sheetUp.indexOf("MA") !== -1) unitVal = "MA";
      else if (sheetUp.indexOf("SMK") !== -1) unitVal = "SMK";
      else unitVal = "MTs";
    }

    students.push({
      id: idVal,
      nisn: nisnVal,
      name: nameVal,
      gender: (colGender >= 0 && colGender < row.length) ? String(row[colGender] || "Laki-laki").trim() : "Laki-laki",
      className: (colClass >= 0 && colClass < row.length) ? String(row[colClass] || "-").trim() : "-",
      unitPendidikan: unitVal,
      dormitory: (colDorm >= 0 && colDorm < row.length) ? String(row[colDorm] || "-").trim() : "-",
      parentName: (colParent >= 0 && colParent < row.length) ? String(row[colParent] || "-").trim() : "-",
      parentPhone: (colPhone >= 0 && colPhone < row.length) ? String(row[colPhone] || "").trim() : "",
      status: (colStatus >= 0 && colStatus < row.length) ? String(row[colStatus] || "Siswa").trim() : "Siswa"
    });
  }

  return students;
}

function getAllAttendanceLogs() {
  const ss = getSafeSpreadsheet();
  const logSheet = findSheet(ss, LOG_SHEET_NAMES);
  if (!logSheet) return [];
  const data = logSheet.getDataRange().getValues();
  const logs = [];
  for (var i = data.length - 1; i >= 1; i--) {
    var rowId = String(data[i][1] || "").trim();
    if (!rowId) continue;
    logs.push({
      timestamp: formatDateString(data[i][0]),
      id: rowId,
      nama: String(data[i][2] || ""),
      kelas: String(data[i][3] || ""),
      unit: String(data[i][4] || ""),
      asrama: String(data[i][5] || ""),
      sesi: String(data[i][6] || ""),
      status: String(data[i][7] || "Hadir"),
      petugas: String(data[i][8] || ""),
      catatan: String(data[i][9] || "")
    });
    if (logs.length >= 250) break;
  }
  return logs;
}

function handleRecordAttendance(data) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    const ss = getSafeSpreadsheet();
    if (!ss) return createJsonResponse({ status: "error", message: "Spreadsheet database tidak terhubung." });

    var logSheet = findSheet(ss, LOG_SHEET_NAMES);
    if (!logSheet) {
      logSheet = getOrCreateSheet(
        ss,
        "Log_Absensi",
        ["Timestamp", "ID", "Nama Siswa", "Kelas", "Unit Pendidikan", "Nama Asrama", "Sesi", "Status", "Petugas Scan", "Catatan"],
        []
      );
    }

    const idSantri = String(data.id || "").trim();
    const studentName = String(data.studentName || "").trim();
    const className = String(data.className || "").trim();
    const unitPendidikan = String(data.unitPendidikan || "").trim();
    const dormitory = String(data.dormitory || "").trim();
    const session = String(data.session || "").trim();
    const date = String(data.date || "").trim();
    const status = String(data.status || "Hadir").trim();
    const scannedBy = String(data.scannedBy || "Guru Piket").trim();
    const notes = String(data.notes || "").trim();

    // Cek duplikasi
    const logValues = logSheet.getDataRange().getValues();
    for (var i = 1; i < logValues.length; i++) {
      var rowTimestamp = logValues[i][0];
      var rowDate = formatDateString(rowTimestamp);
      var rowId = String(logValues[i][1]).trim();
      var rowSession = String(logValues[i][6]).trim();

      if (rowDate === date && rowId === idSantri && rowSession === session) {
        return createJsonResponse({
          status: "already_exists",
          message: studentName + " (" + idSantri + ") SUDAH TERCATAT di sesi " + session
        });
      }
    }

    const timestamp = new Date();
    logSheet.appendRow([
      timestamp,
      idSantri,
      studentName,
      className,
      unitPendidikan,
      dormitory,
      session,
      status,
      scannedBy,
      notes
    ]);

    return createJsonResponse({
      status: "success",
      message: "Absensi " + studentName + " berhasil dicatat.",
      data: { id: idSantri, session: session, status: status }
    });
  } catch (err) {
    return createJsonResponse({ status: "error", message: err.toString() });
  } finally {
    lock.releaseLock();
  }
}

function getStudentAttendanceHistory(searchId) {
  const ss = getSafeSpreadsheet();
  const logSheet = findSheet(ss, LOG_SHEET_NAMES);
  if (!logSheet) return { id: searchId, total: 0, records: [] };
  const data = logSheet.getDataRange().getValues();

  const results = [];
  for (var i = data.length - 1; i >= 1; i--) {
    var rowId = String(data[i][1] || "").trim();
    if (rowId === String(searchId).trim()) {
      results.push({
        timestamp: formatDateString(data[i][0]),
        id: rowId,
        nama: String(data[i][2] || ""),
        kelas: String(data[i][3] || ""),
        unit: String(data[i][4] || ""),
        asrama: String(data[i][5] || ""),
        sesi: String(data[i][6] || ""),
        status: String(data[i][7] || "Hadir"),
        petugas: String(data[i][8] || "")
      });
      if (results.length >= 35) break;
    }
  }

  return { id: searchId, total: results.length, records: results };
}

function formatDateString(val) {
  if (val instanceof Date) {
    return Utilities.formatDate(val, "GMT+7", "yyyy-MM-dd HH:mm");
  }
  return String(val);
}

function createJsonResponse(data) {
  return ContentService.createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

/**
 * Tampilan Web Admin Portal Terpadu (Mandiri Tanpa File Eksternal)
 */
function getAdminDashboardHtml() {
  return `$adminPortalHtml`;
}
    """.trimIndent()
}

private fun getAdminDashboardHtml(): String {
    return WebAdminPortalTemplate.getAdminPortalHtml()
}

private fun getOldAdminDashboardHtml(): String {
    return """
<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="UTF-8">
  <title>DIABDI - Web Admin Presensi Digital Santri</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-slate-100 min-h-screen text-slate-800 font-sans">
  <!-- Header -->
  <header class="bg-emerald-900 text-white p-4 shadow-md sticky top-0 z-50">
    <div class="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center gap-3">
      <div class="flex items-center gap-3">
        <span class="text-3xl">🕌</span>
        <div>
          <h1 class="text-xl font-bold tracking-wide">DIABDI Web Admin</h1>
          <p class="text-xs text-emerald-200">Digitalisasi Informasi Absensi Berbasis Data Indeks</p>
        </div>
      </div>
      <div class="flex items-center gap-3">
        <div class="bg-emerald-800/80 px-3 py-1.5 rounded-xl border border-emerald-700 text-xs">
          <span class="text-emerald-300">Login Role:</span> 
          <span id="roleBadge" class="font-bold text-white uppercase ml-1">Superadmin (Semua Unit)</span>
        </div>
        <select id="simulasiRole" onchange="gantiRole()" class="text-xs bg-emerald-800 text-white border border-emerald-600 rounded-lg px-2 py-1.5">
          <option value="SUPERADMIN">Login: Superadmin (Semua Unit)</option>
          <option value="ADMIN_MTS">Login: Admin Unit MTs</option>
          <option value="ADMIN_MA">Login: Admin Unit MA</option>
          <option value="ADMIN_SMK">Login: Admin Unit SMK</option>
        </select>
      </div>
    </div>
  </header>

  <!-- Navigation Tabs -->
  <nav class="bg-white border-b border-slate-200 shadow-sm sticky top-[72px] z-40">
    <div class="max-w-7xl mx-auto flex overflow-x-auto text-sm font-semibold">
      <button onclick="switchTab('siswa')" id="tabBtn-siswa" class="px-5 py-3.5 border-b-2 border-emerald-600 text-emerald-800 flex items-center gap-2 whitespace-nowrap">
        <span>📋</span> 1. Data Siswa
      </button>
      <button onclick="switchTab('log')" id="tabBtn-log" class="px-5 py-3.5 border-b-2 border-transparent text-slate-600 hover:text-slate-900 flex items-center gap-2 whitespace-nowrap">
        <span>⏱️</span> 2. Log Absensi Realtime
      </button>
      <button onclick="switchTab('rekap')" id="tabBtn-rekap" class="px-5 py-3.5 border-b-2 border-transparent text-slate-600 hover:text-slate-900 flex items-center gap-2 whitespace-nowrap">
        <span>📊</span> 3. Rekap Absensi & Download
      </button>
      <button onclick="switchTab('jam')" id="tabBtn-jam" class="px-5 py-3.5 border-b-2 border-transparent text-slate-600 hover:text-slate-900 flex items-center gap-2 whitespace-nowrap">
        <span>⚙️</span> 4. Jam Sekolah Unit
      </button>
    </div>
  </nav>

  <main class="max-w-7xl mx-auto p-4 md:p-6 space-y-6">

    <!-- TAB 1: DATA SISWA -->
    <section id="panel-siswa" class="space-y-4">
      <div class="bg-white p-5 rounded-2xl shadow-sm border border-slate-200">
        <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-4">
          <div>
            <h2 class="text-base font-bold text-slate-800">📋 Database Indeks Santri</h2>
            <p class="text-xs text-slate-500">Santri login menggunakan ID sebagai Username dan NISN sebagai Password</p>
          </div>
          <div class="flex flex-wrap items-center gap-2 w-full md:w-auto">
            <select id="filterUnitSiswa" onchange="renderSiswa()" class="text-xs border px-3 py-2 rounded-xl bg-slate-50 font-medium">
              <option value="Semua">Semua Unit</option>
              <option value="MTs">Unit MTs</option>
              <option value="MA">Unit MA</option>
              <option value="SMK">Unit SMK</option>
            </select>
            <select id="filterKelasSiswa" onchange="renderSiswa()" class="text-xs border px-3 py-2 rounded-xl bg-slate-50 font-medium">
              <option value="Semua">Semua Kelas</option>
              <option value="7A">Kelas 7A</option>
              <option value="7B">Kelas 7B</option>
              <option value="8A">Kelas 8A</option>
              <option value="9A">Kelas 9A</option>
              <option value="10-IPA">Kelas 10-IPA</option>
              <option value="11-IPA">Kelas 11-IPA</option>
              <option value="10-TKJ">Kelas 10-TKJ</option>
              <option value="11-RPL">Kelas 11-RPL</option>
            </select>
            <input type="text" id="cariSiswa" onkeyup="renderSiswa()" placeholder="Cari ID / Nama..." class="text-xs border px-3 py-2 rounded-xl bg-slate-50 flex-1 md:w-44">
            <button onclick="tambahSiswa()" class="bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold px-3.5 py-2 rounded-xl flex items-center gap-1.5 whitespace-nowrap">
              <span>+</span> Tambah Santri
            </button>
          </div>
        </div>

        <div class="overflow-x-auto rounded-xl border border-slate-200">
          <table class="w-full text-left text-xs border-collapse">
            <thead class="bg-slate-100 text-slate-700 font-semibold border-b">
              <tr>
                <th class="p-3">ID (User)</th>
                <th class="p-3">NISN (Pass)</th>
                <th class="p-3">Nama Santri</th>
                <th class="p-3">Unit</th>
                <th class="p-3">Kelas</th>
                <th class="p-3">Asrama</th>
                <th class="p-3">Wali Santri</th>
                <th class="p-3">No. WhatsApp</th>
                <th class="p-3 text-center">Aksi</th>
              </tr>
            </thead>
            <tbody id="bodyTabelSiswa" class="divide-y divide-slate-100 bg-white">
              <!-- Render otomatis via JavaScript -->
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- TAB 2: LOG ABSENSI -->
    <section id="panel-log" class="hidden space-y-4">
      <div class="bg-white p-5 rounded-2xl shadow-sm border border-slate-200">
        <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-4">
          <div>
            <h2 class="text-base font-bold text-slate-800">⏱️ Log Riwayat Absensi Realtime</h2>
            <p class="text-xs text-slate-500">Rekaman scan QR oleh guru piket per 7 sesi ibadah & sekolah</p>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <select id="filterSesiLog" onchange="renderLog()" class="text-xs border px-3 py-2 rounded-xl bg-slate-50">
              <option value="Semua">Semua Sesi</option>
              <option value="Sholat Subuh">Sholat Subuh</option>
              <option value="Masuk Sekolah">Masuk Sekolah</option>
              <option value="Sholat Dzuhur">Sholat Dzuhur</option>
              <option value="Sholat Ashar">Sholat Ashar</option>
              <option value="Masuk Asrama">Masuk Asrama</option>
              <option value="Sholat Maghrib">Sholat Maghrib</option>
              <option value="Sholat Isya/KBM">Sholat Isya/KBM</option>
            </select>
            <select id="filterStatusLog" onchange="renderLog()" class="text-xs border px-3 py-2 rounded-xl bg-slate-50">
              <option value="Semua">Semua Status</option>
              <option value="Hadir">Hadir</option>
              <option value="Terlambat">Terlambat</option>
              <option value="Izin">Izin</option>
            </select>
          </div>
        </div>

        <div class="overflow-x-auto rounded-xl border border-slate-200">
          <table class="w-full text-left text-xs border-collapse">
            <thead class="bg-slate-100 text-slate-700 font-semibold border-b">
              <tr>
                <th class="p-3">Waktu</th>
                <th class="p-3">ID</th>
                <th class="p-3">Nama Santri</th>
                <th class="p-3">Unit</th>
                <th class="p-3">Kelas</th>
                <th class="p-3">Sesi</th>
                <th class="p-3">Status</th>
                <th class="p-3">Petugas Scan</th>
              </tr>
            </thead>
            <tbody id="bodyTabelLog" class="divide-y divide-slate-100 bg-white">
              <!-- Render otomatis via JavaScript -->
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- TAB 3: REKAP ABSENSI & DOWNLOAD DENGAN FILTER KELAS -->
    <section id="panel-rekap" class="hidden space-y-4">
      <div class="bg-white p-5 rounded-2xl shadow-sm border border-slate-200">
        <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-4">
          <div>
            <h2 class="text-base font-bold text-slate-800">📊 Rekapitulasi Presensi Santri</h2>
            <p class="text-xs text-slate-500">Filter rekap per kelas dan unduh langsung file laporan (CSV / Excel)</p>
          </div>
          <!-- Filter Kelas & Tombol Download -->
          <div class="flex flex-wrap items-center gap-2">
            <label class="text-xs font-bold text-slate-700">Filter Kelas:</label>
            <select id="filterKelasRekap" onchange="renderRekap()" class="text-xs border-2 border-emerald-600 px-3 py-2 rounded-xl bg-emerald-50 text-emerald-900 font-bold">
              <option value="Semua">Semua Kelas</option>
              <option value="7A">Kelas 7A</option>
              <option value="7B">Kelas 7B</option>
              <option value="8A">Kelas 8A</option>
              <option value="9A">Kelas 9A</option>
              <option value="10-IPA">Kelas 10-IPA</option>
              <option value="11-IPA">Kelas 11-IPA</option>
              <option value="10-TKJ">Kelas 10-TKJ</option>
              <option value="11-RPL">Kelas 11-RPL</option>
            </select>
            <button onclick="downloadRekapExcel()" class="bg-emerald-700 hover:bg-emerald-800 text-white text-xs font-bold px-4 py-2 rounded-xl flex items-center gap-2 shadow-sm transition">
              <span>📥</span> Download Rekap (Filter Kelas)
            </button>
          </div>
        </div>

        <!-- Tabel Rekap -->
        <div class="overflow-x-auto rounded-xl border border-slate-200">
          <table class="w-full text-left text-xs border-collapse">
            <thead class="bg-emerald-50 text-emerald-950 font-bold border-b border-emerald-100">
              <tr>
                <th class="p-3">No</th>
                <th class="p-3">ID Santri</th>
                <th class="p-3">Nama Santri</th>
                <th class="p-3">Unit</th>
                <th class="p-3">Kelas</th>
                <th class="p-3 text-center">Hadir</th>
                <th class="p-3 text-center">Terlambat</th>
                <th class="p-3 text-center">Izin</th>
                <th class="p-3 text-center">Alpha</th>
                <th class="p-3 text-center">% Kehadiran</th>
              </tr>
            </thead>
            <tbody id="bodyTabelRekap" class="divide-y divide-slate-100 bg-white">
              <!-- Render otomatis via JavaScript -->
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- TAB 4: JAM SEKOLAH PER UNIT -->
    <section id="panel-jam" class="hidden space-y-4">
      <div class="bg-white p-5 rounded-2xl shadow-sm border border-slate-200">
        <h2 class="text-base font-bold text-slate-800 mb-1">⏱️ Pengaturan Jam Masuk Sekolah per Unit</h2>
        <p class="text-xs text-slate-500 mb-4">Setiap unit pendidikan memiliki batas jam masuk dan toleransi waktu keterlambatan masing-masing</p>

        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div class="bg-emerald-50 p-4 rounded-xl border border-emerald-200">
            <h3 class="font-bold text-emerald-900 text-sm">Unit MTs</h3>
            <p class="text-xs text-emerald-700 mb-2">Madrasah Tsanawiyah (Kelas 7 - 9)</p>
            <input type="text" id="jamMts" value="06:30 - 07:00" class="w-full text-sm p-2 border rounded-lg bg-white">
          </div>
          <div class="bg-blue-50 p-4 rounded-xl border border-blue-200">
            <h3 class="font-bold text-blue-900 text-sm">Unit MA</h3>
            <p class="text-xs text-blue-700 mb-2">Madrasah Aliyah (Kelas 10 - 12)</p>
            <input type="text" id="jamMa" value="06:45 - 07:15" class="w-full text-sm p-2 border rounded-lg bg-white">
          </div>
          <div class="bg-amber-50 p-4 rounded-xl border border-amber-200">
            <h3 class="font-bold text-amber-900 text-sm">Unit SMK</h3>
            <p class="text-xs text-amber-700 mb-2">SMK Kejuruan (TKJ / RPL)</p>
            <input type="text" id="jamSmk" value="07:00 - 07:30" class="w-full text-sm p-2 border rounded-lg bg-white">
          </div>
        </div>
        <button onclick="alert('Pengaturan jam unit berhasil disimpan ke spreadsheet!')" class="mt-4 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold px-4 py-2.5 rounded-xl">
          Simpan Jam Unit ke Spreadsheet
        </button>
      </div>
    </section>

  </main>

  <script>
    // Master data
    let currentRole = "SUPERADMIN"; // "SUPERADMIN", "ADMIN_MTS", "ADMIN_MA", "ADMIN_SMK"
    let assignedUnit = "Semua";

    let siswaList = [
      { id: "532303019", nisn: "20277594", name: "M. Yunus Fathir", gender: "Laki-laki", className: "7A", unitPendidikan: "MTs", dormitory: "Asrama Abu Bakar", parentName: "Bpk. Edi", parentPhone: "081234567890", status: "Siswa" },
      { id: "532303020", nisn: "20277595", name: "Ahmad Zaki", gender: "Laki-laki", className: "7A", unitPendidikan: "MTs", dormitory: "Asrama Abu Bakar", parentName: "Bpk. Rahmad", parentPhone: "081298765432", status: "Siswa" },
      { id: "532303021", nisn: "20277596", name: "Fathir Rahman Hakim", gender: "Laki-laki", className: "10-IPA", unitPendidikan: "MA", dormitory: "Asrama Utsman", parentName: "Drs. Lukman Hakim", parentPhone: "081555667788", status: "Siswa" },
      { id: "532303022", nisn: "20277597", name: "Nabila Syarifah Azzahra", gender: "Perempuan", className: "10-TKJ", unitPendidikan: "SMK", dormitory: "Asrama Aisyah", parentName: "Dr. Farhan Syarif", parentPhone: "081733445566", status: "Siswa" }
    ];

    let logAbsensiList = [
      { timestamp: "2026-10-03 06:48", id: "532303019", nama: "M. Yunus Fathir", unit: "MTs", kelas: "7A", sesi: "Masuk Sekolah", status: "Hadir", petugas: "Ustadz Fauzi" },
      { timestamp: "2026-10-03 06:55", id: "532303020", nama: "Ahmad Zaki", unit: "MTs", kelas: "7A", sesi: "Masuk Sekolah", status: "Hadir", petugas: "Ustadz Fauzi" },
      { timestamp: "2026-10-03 07:05", id: "532303021", nama: "Fathir Rahman Hakim", unit: "MA", kelas: "10-IPA", sesi: "Masuk Sekolah", status: "Hadir", petugas: "Ustadzah Fatimah" },
      { timestamp: "2026-10-03 07:22", id: "532303022", nama: "Nabila Syarifah", unit: "SMK", kelas: "10-TKJ", sesi: "Masuk Sekolah", status: "Terlambat", petugas: "Pak Budi" }
    ];

    function gantiRole() {
      const val = document.getElementById('simulasiRole').value;
      currentRole = val;
      if (val === "SUPERADMIN") {
        assignedUnit = "Semua";
        document.getElementById('roleBadge').innerText = "Superadmin (Semua Unit)";
        document.getElementById('roleBadge').className = "font-bold text-white uppercase ml-1";
      } else if (val === "ADMIN_MTS") {
        assignedUnit = "MTs";
        document.getElementById('roleBadge').innerText = "Admin Unit MTs";
        document.getElementById('roleBadge').className = "font-bold text-emerald-200 uppercase ml-1";
      } else if (val === "ADMIN_MA") {
        assignedUnit = "MA";
        document.getElementById('roleBadge').innerText = "Admin Unit MA";
        document.getElementById('roleBadge').className = "font-bold text-blue-200 uppercase ml-1";
      } else if (val === "ADMIN_SMK") {
        assignedUnit = "SMK";
        document.getElementById('roleBadge').innerText = "Admin Unit SMK";
        document.getElementById('roleBadge').className = "font-bold text-amber-200 uppercase ml-1";
      }

      // Kunci filter jika admin unit
      const filterUnit = document.getElementById('filterUnitSiswa');
      if (assignedUnit !== "Semua") {
        filterUnit.value = assignedUnit;
        filterUnit.disabled = true;
      } else {
        filterUnit.disabled = false;
      }
      renderSiswa();
      renderLog();
      renderRekap();
    }

    function switchTab(tab) {
      ['siswa', 'log', 'rekap', 'jam'].forEach(t => {
        document.getElementById('panel-' + t).classList.add('hidden');
        document.getElementById('tabBtn-' + t).className = 'px-5 py-3.5 border-b-2 border-transparent text-slate-600 hover:text-slate-900 flex items-center gap-2 whitespace-nowrap';
      });
      document.getElementById('panel-' + tab).classList.remove('hidden');
      document.getElementById('tabBtn-' + tab).className = 'px-5 py-3.5 border-b-2 border-emerald-600 text-emerald-800 flex items-center gap-2 whitespace-nowrap';
    }

    function renderSiswa() {
      const filterUnit = document.getElementById('filterUnitSiswa').value;
      const filterKelas = document.getElementById('filterKelasSiswa').value;
      const query = document.getElementById('cariSiswa').value.toLowerCase().trim();
      const tbody = document.getElementById('bodyTabelSiswa');
      tbody.innerHTML = '';

      const filtered = siswaList.filter(s => {
        const cocokUnit = (assignedUnit !== "Semua" ? s.unitPendidikan === assignedUnit : (filterUnit === "Semua" || s.unitPendidikan === filterUnit));
        const cocokKelas = (filterKelas === "Semua" || s.className === filterKelas);
        const cocokQuery = (!query || s.id.toLowerCase().includes(query) || s.name.toLowerCase().includes(query));
        return cocokUnit && cocokKelas && cocokQuery;
      });

      if (filtered.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="p-6 text-center text-slate-400">Tidak ada data santri yang cocok dengan filter</td></tr>';
        return;
      }

      filtered.forEach(s => {
        const tr = document.createElement('tr');
        tr.className = 'hover:bg-slate-50 transition';
        tr.innerHTML = '<td class="p-3 font-bold text-emerald-800 font-mono">' + s.id + '</td>' +
          '<td class="p-3 font-mono text-slate-600">' + s.nisn + '</td>' +
          '<td class="p-3 font-bold text-slate-800">' + s.name + '</td>' +
          '<td class="p-3"><span class="px-2 py-0.5 rounded text-2xs font-bold ' + (s.unitPendidikan === "MTs" ? "bg-emerald-100 text-emerald-800" : (s.unitPendidikan === "MA" ? "bg-blue-100 text-blue-800" : "bg-amber-100 text-amber-800")) + '">' + s.unitPendidikan + '</span></td>' +
          '<td class="p-3 font-semibold">' + s.className + '</td>' +
          '<td class="p-3">' + s.dormitory + '</td>' +
          '<td class="p-3">' + s.parentName + '</td>' +
          '<td class="p-3 text-slate-500 font-mono">' + s.parentPhone + '</td>' +
          '<td class="p-3 text-center"><button onclick="hapusSiswa(\'' + s.id + '\')" class="text-red-600 hover:text-red-800 font-bold text-xs">Hapus</button></td>';
        tbody.appendChild(tr);
      });
    }

    function hapusSiswa(id) {
      if (!confirm('Hapus santri ID ' + id + '?')) return;
      siswaList = siswaList.filter(s => s.id !== id);
      renderSiswa();
      renderRekap();
    }

    function tambahSiswa() {
      const id = prompt('Masukkan ID Santri (Username):');
      if (!id) return;
      const nisn = prompt('Masukkan NISN Santri (Password):');
      if (!nisn) return;
      const nama = prompt('Masukkan Nama Lengkap:');
      if (!nama) return;
      const unit = (assignedUnit !== "Semua") ? assignedUnit : prompt('Unit Pendidikan (MTs/MA/SMK):', 'MTs');
      const kelas = prompt('Kelas (contoh: 7A, 10-IPA):', '7A');

      siswaList.push({
        id: id.trim(),
        nisn: nisn.trim(),
        name: nama.trim(),
        gender: "Laki-laki",
        className: kelas.trim(),
        unitPendidikan: unit.trim(),
        dormitory: "Asrama Santri",
        parentName: "Wali Santri",
        parentPhone: "0812000000",
        status: "Siswa"
      });
      renderSiswa();
      renderRekap();
    }

    function renderLog() {
      const filterSesi = document.getElementById('filterSesiLog').value;
      const filterStatus = document.getElementById('filterStatusLog').value;
      const tbody = document.getElementById('bodyTabelLog');
      tbody.innerHTML = '';

      const filtered = logAbsensiList.filter(l => {
        const cocokUnit = (assignedUnit === "Semua" || l.unit === assignedUnit);
        const cocokSesi = (filterSesi === "Semua" || l.sesi === filterSesi);
        const cocokStatus = (filterStatus === "Semua" || l.status === filterStatus);
        return cocokUnit && cocokSesi && cocokStatus;
      });

      if (filtered.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="p-6 text-center text-slate-400">Belum ada rekaman absensi</td></tr>';
        return;
      }

      filtered.forEach(l => {
        const tr = document.createElement('tr');
        tr.className = 'hover:bg-slate-50';
        tr.innerHTML = '<td class="p-3 text-slate-500 font-mono">' + l.timestamp + '</td>' +
          '<td class="p-3 font-mono font-bold">' + l.id + '</td>' +
          '<td class="p-3 font-semibold text-slate-800">' + l.nama + '</td>' +
          '<td class="p-3"><span class="px-2 py-0.5 rounded text-2xs font-bold bg-slate-100">' + l.unit + '</span></td>' +
          '<td class="p-3">' + l.kelas + '</td>' +
          '<td class="p-3 font-medium">' + l.sesi + '</td>' +
          '<td class="p-3"><span class="px-2 py-0.5 rounded font-bold text-2xs ' + (l.status === 'Hadir' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800') + '">' + l.status + '</span></td>' +
          '<td class="p-3 text-slate-600">' + l.petugas + '</td>';
        tbody.appendChild(tr);
      });
    }

    function renderRekap() {
      const filterKelas = document.getElementById('filterKelasRekap').value;
      const tbody = document.getElementById('bodyTabelRekap');
      tbody.innerHTML = '';

      const filtered = siswaList.filter(s => {
        const cocokUnit = (assignedUnit === "Semua" || s.unitPendidikan === assignedUnit);
        const cocokKelas = (filterKelas === "Semua" || s.className === filterKelas);
        return cocokUnit && cocokKelas;
      });

      if (filtered.length === 0) {
        tbody.innerHTML = '<tr><td colspan="10" class="p-6 text-center text-slate-400">Tidak ada santri pada filter kelas ini</td></tr>';
        return;
      }

      filtered.forEach((s, idx) => {
        // Hitung kehadiran simulasi
        const totalHadir = 6;
        const totalTelat = 1;
        const totalIzin = 0;
        const totalAlpha = 0;
        const persentase = 100;

        const tr = document.createElement('tr');
        tr.className = 'hover:bg-emerald-50/50';
        tr.innerHTML = '<td class="p-3 font-bold text-slate-400">' + (idx + 1) + '</td>' +
          '<td class="p-3 font-mono font-bold text-emerald-800">' + s.id + '</td>' +
          '<td class="p-3 font-bold text-slate-800">' + s.name + '</td>' +
          '<td class="p-3"><span class="px-2 py-0.5 rounded text-2xs font-bold bg-slate-100">' + s.unitPendidikan + '</span></td>' +
          '<td class="p-3 font-bold">' + s.className + '</td>' +
          '<td class="p-3 text-center text-emerald-700 font-bold">' + totalHadir + '</td>' +
          '<td class="p-3 text-center text-amber-600 font-bold">' + totalTelat + '</td>' +
          '<td class="p-3 text-center text-blue-600">' + totalIzin + '</td>' +
          '<td class="p-3 text-center text-red-600">' + totalAlpha + '</td>' +
          '<td class="p-3 text-center"><span class="px-2 py-0.5 rounded font-bold bg-emerald-100 text-emerald-900 text-2xs">' + persentase + '%</span></td>';
        tbody.appendChild(tr);
      });
    }

    // FITUR UTAMA: DOWNLOAD DENGAN FILTER KELAS
    function downloadRekapExcel() {
      const filterKelas = document.getElementById('filterKelasRekap').value;
      const filtered = siswaList.filter(s => {
        const cocokUnit = (assignedUnit === "Semua" || s.unitPendidikan === assignedUnit);
        const cocokKelas = (filterKelas === "Semua" || s.className === filterKelas);
        return cocokUnit && cocokKelas;
      });

      if (filtered.length === 0) {
        alert('Tidak ada data santri untuk kelas ' + filterKelas);
        return;
      }

      // Generate CSV Content
      let csv = "No,ID Santri,NISN,Nama Santri,Unit Pendidikan,Kelas,Hadir,Terlambat,Izin,Alpha,Persentase Kehadiran\n";
      filtered.forEach((s, idx) => {
        csv += (idx + 1) + ',"' + s.id + '","' + s.nisn + '","' + s.name + '","' + s.unitPendidikan + '","' + s.className + '",6,1,0,0,100%\n';
      });

      // Trigger Browser Download
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      const tanggal = new Date().toISOString().slice(0, 10);
      link.setAttribute('href', url);
      link.setAttribute('download', 'DIABDI_Rekap_Absensi_Kelas_' + filterKelas + '_' + tanggal + '.csv');
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      alert('File rekap absensi kelas ' + filterKelas + ' berhasil diunduh!');
    }

    // Inisialisasi awal
    renderSiswa();
    renderLog();
    renderRekap();
  </script>
</body>
</html>
    """.trimIndent()
}

private fun getParentPortalHtml(): String {
    return """
<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="UTF-8">
  <title>Portal Cek Absensi Santri</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-slate-50 min-h-screen text-slate-800 font-sans p-4">
  <div class="max-w-md mx-auto bg-white rounded-2xl shadow-lg border border-slate-100 overflow-hidden">
    <div class="bg-emerald-700 p-6 text-white text-center">
      <div class="text-3xl mb-1">🕌</div>
      <h1 class="text-xl font-bold">Portal Absensi Santri</h1>
      <p class="text-xs text-emerald-100 mt-1">Pantau 7 Sesi Ibadah & Sekolah Ananda Berbasis ID</p>
    </div>

    <div class="p-5">
      <label class="block text-xs font-semibold text-slate-600 mb-1">Masukkan ID Santri (contoh: SAN-001):</label>
      <div class="flex gap-2">
        <input type="text" id="inputId" placeholder="SAN-001" 
          class="flex-1 px-4 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 uppercase">
        <button onclick="cariAbsensi()" 
          class="bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-xl text-sm font-semibold">
          Cari
        </button>
      </div>

      <div id="loader" class="hidden text-center py-6 text-sm text-slate-500">Sedang memuat data...</div>

      <div id="hasil" class="mt-5 hidden space-y-3">
        <div class="bg-emerald-50 p-3 rounded-xl border border-emerald-200">
          <p id="studentName" class="font-bold text-emerald-900"></p>
          <p id="studentClass" class="text-xs text-emerald-700"></p>
        </div>
        <h3 class="text-xs font-bold text-slate-500 tracking-wider">RIWAYAT 7 SESI TERAKHIR:</h3>
        <div id="listRiwayat" class="space-y-2"></div>
      </div>
    </div>
  </div>

  <script>
    function cariAbsensi() {
      const id = document.getElementById('inputId').value.trim();
      if (!id) return alert('Masukkan ID Santri terlebih dahulu');

      document.getElementById('loader').classList.remove('hidden');
      document.getElementById('hasil').classList.add('hidden');

      fetch('?id=' + encodeURIComponent(id))
        .then(res => res.json())
        .then(data => {
          document.getElementById('loader').classList.add('hidden');
          if (!data.records || data.records.length === 0) {
            alert('Data kehadiran tidak ditemukan untuk ID tersebut.');
            return;
          }
          document.getElementById('studentName').innerText = data.records[0].nama;
          document.getElementById('studentClass').innerText = 'Unit ' + data.records[0].unit + ' • Kelas ' + data.records[0].kelas + ' • ' + data.records[0].asrama;
          
          const list = document.getElementById('listRiwayat');
          list.innerHTML = '';
          data.records.forEach(r => {
            const el = document.createElement('div');
            el.className = 'p-3 bg-slate-50 rounded-xl border border-slate-100 flex justify-between items-center text-xs';
            el.innerHTML = '<div><p class="font-bold text-slate-800">' + r.sesi + '</p><p class="text-slate-500">' + r.timestamp + ' WIB</p></div>' +
              '<span class="px-2 py-1 bg-emerald-100 text-emerald-800 font-bold rounded-lg">' + r.status + '</span>';
            list.appendChild(el);
          });
          document.getElementById('hasil').classList.remove('hidden');
        })
        .catch(err => {
          document.getElementById('loader').classList.add('hidden');
          alert('Terjadi kesalahan koneksi');
        });
    }
  </script>
</body>
</html>
    """.trimIndent()
}
