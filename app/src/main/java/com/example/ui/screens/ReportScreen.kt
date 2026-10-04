package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttendanceSession
import com.example.ui.SantriViewModel

@Composable
fun ReportScreen(
    viewModel: SantriViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allRecords by viewModel.allAttendanceRecords.collectAsState()
    val todayRecords by viewModel.todayAllRecords.collectAsState()
    val students by viewModel.studentsList.collectAsState()

    var selectedUnitFilter by remember { mutableStateOf("Semua") }
    var selectedSessionFilter by remember { mutableStateOf("Semua Sesi") }

    val filteredRecords = remember(allRecords, selectedUnitFilter, selectedSessionFilter) {
        allRecords.filter { rec ->
            val matchUnit = if (selectedUnitFilter == "Semua") true else rec.unitPendidikan.equals(selectedUnitFilter, ignoreCase = true)
            val matchSession = if (selectedSessionFilter == "Semua Sesi") true else rec.sessionName == selectedSessionFilter
            matchUnit && matchSession
        }
    }

    val totalScans = todayRecords.size
    val hadirCount = todayRecords.count { it.status == "Hadir" }
    val terlambatCount = todayRecords.count { it.status == "Terlambat" }
    val totalStudents = if (students.isNotEmpty()) students.size else 75

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("report_screen_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Rekapitulasi Absensi 7 Sesi",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Terintegrasi 3 Unit Pendidikan (MTs, MA, SMK)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Unit Filter Tabs (MTs, MA, SMK)
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val unitOptions = listOf("Semua", "MTs", "MA", "SMK")
                items(unitOptions) { u ->
                    ElevatedFilterChip(
                        selected = selectedUnitFilter == u,
                        onClick = { selectedUnitFilter = u },
                        label = { Text(if (u == "Semua") "Semua Unit" else "Unit $u") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Summary Statistics Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Total Hari Ini",
                    value = "$totalScans Scan",
                    subtitle = "dari $totalStudents Santri",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Hadir Tepat",
                    value = "$hadirCount",
                    subtitle = "Tepat Waktu",
                    color = Color(0xFF16A34A),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Terlambat",
                    value = "$terlambatCount",
                    subtitle = "Perlu Bimbingan",
                    color = Color(0xFFD97706),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 7 Sessions Progress Overview
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Statistik 7 Sesi Harian (Hari Ini)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    AttendanceSession.ALL_SESSIONS.forEach { session ->
                        val count = todayRecords.count { it.sessionName == session.name }
                        val progress = if (totalStudents > 0) (count.toFloat() / totalStudents).coerceIn(0f, 1f) else 0f

                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${session.id}. ${session.name}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "$count / $totalStudents (${(progress * 100).toInt()}%)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = session.accentColor
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                color = session.accentColor,
                                trackColor = session.accentColor.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }

        // Export / Copy Button for WhatsApp group
        item {
            Button(
                onClick = {
                    val reportText = buildDailyReportSummary(todayRecords, totalStudents, selectedUnitFilter)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Rekap Absensi", reportText))
                    Toast.makeText(context, "Rekap berhasil disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("copy_daily_report_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salin Format Rekap WhatsApp Guru/Pengasuh")
            }
        }

        // Filter Sesi Chips
        item {
            Text(
                text = "Riwayat Catatan Absensi",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val sessions = listOf("Semua Sesi") + AttendanceSession.ALL_SESSIONS.map { it.name }
                items(sessions) { s ->
                    ElevatedFilterChip(
                        selected = selectedSessionFilter == s,
                        onClick = { selectedSessionFilter = s },
                        label = { Text(s) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Records List
        items(filteredRecords.take(50), key = { it.id }) { rec ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rec.studentName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = rec.unitPendidikan,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Text(
                            text = "ID: ${rec.studentId} • ${rec.sessionName} • ${rec.time} WIB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${rec.className} • ${rec.dormitory}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Surface(
                        color = if (rec.status == "Hadir") Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = rec.status,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (rec.status == "Hadir") Color(0xFF16A34A) else Color(0xFFD97706),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun buildDailyReportSummary(todayRecords: List<com.example.data.model.AttendanceRecord>, totalStudents: Int, unitFilter: String): String {
    val hadir = todayRecords.count { it.status == "Hadir" }
    val terlambat = todayRecords.count { it.status == "Terlambat" }
    val dateStr = if (todayRecords.isNotEmpty()) todayRecords.first().date else "Hari ini"

    val sessionDetails = AttendanceSession.ALL_SESSIONS.joinToString("\n") { s ->
        val count = todayRecords.count { it.sessionName == s.name }
        "  • ${s.name}: $count santri"
    }

    return """
        📋 *LAPORAN REKAPITULASI ABSENSI HARIAN PESANTREN & SEKOLAH*
        Unit: $unitFilter (MTs / MA / SMK)
        Tanggal: $dateStr
        Total Santri: $totalStudents Orang
        
        📊 *Ringkasan Kehadiran:*
        - Total Hadir Tepat Waktu: $hadir
        - Catatan Terlambat: $terlambat
        
        🕌 *Detail Per Sesi:*
        $sessionDetails
        
        _Data otomatis terekap melalui Sistem SantriScan QR & Google Sheets._
    """.trimIndent()
}
