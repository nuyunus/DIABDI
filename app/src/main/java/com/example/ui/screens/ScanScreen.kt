package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttendanceRecord
import com.example.data.remote.WhatsAppHelper
import com.example.ui.SantriViewModel
import com.example.ui.components.ScanResultCard
import com.example.ui.components.ScannerCameraView
import com.example.ui.components.SessionSelectorRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScanScreen(
    viewModel: SantriViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val sessionRecords by viewModel.currentSessionRecords.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val feedback by viewModel.scanFeedback.collectAsState()
    val sampleStudents by viewModel.studentsList.collectAsState()
    val isBypassed by viewModel.isTimeLockBypassed.collectAsState()

    val teacherUnitSchedule = viewModel.getCurrentTeacherUnitSchedule()
    val isSessionActive = viewModel.isCurrentSessionActive()

    val timeFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
    val todayFormatted = timeFormat.format(Date())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .testTag("scan_screen_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Teacher Profile & Assigned Unit Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUser?.displayName ?: "Petugas Guru",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                if (currentUser?.unitPendidikan != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.White.copy(alpha = 0.25f)
                                    ) {
                                        Text(
                                            text = "Unit ${currentUser?.unitPendidikan}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Hanya dapat scan santri Unit ${currentUser?.unitPendidikan ?: "Semua"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        // Cloud Status Indicator (Read-only untuk Guru, sinkronisasi hanya oleh Superadmin)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (unsyncedCount > 0) Color(0xFFFEF08A) else Color.White.copy(alpha = 0.95f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (unsyncedCount > 0) Icons.Default.CloudSync else Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = if (unsyncedCount > 0) Color(0xFF854D0E) else Color(0xFF16A34A),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (unsyncedCount > 0) "$unsyncedCount Antre" else "Cloud OK",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (unsyncedCount > 0) Color(0xFF854D0E) else Color(0xFF16A34A)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7 Attendance Sessions Selector
        item {
            SessionSelectorRow(
                selectedSession = currentSession,
                onSessionSelected = { viewModel.selectSession(it) }
            )
        }

        // Session Lock Status & Demo Bypass Toggle
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSessionActive) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSessionActive) Color(0xFFBBF7D0) else Color(0xFFFECACA)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isSessionActive) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isSessionActive) Color(0xFF16A34A) else Color(0xFFDC2626),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isSessionActive) "SESI AKTIF (Absensi Dibuka)" else "SESI TERKUNCI (Di Luar Jam Sesi)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSessionActive) Color(0xFF166534) else Color(0xFF991B1B)
                            )
                            Text(
                                text = "Jadwal: ${currentSession.getEffectiveTimeRange(teacherUnitSchedule)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSessionActive) Color(0xFF15803D) else Color(0xFFB91C1C)
                            )
                        }
                    }

                    // Tester bypass switch
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Mode Uji Coba",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                        Switch(
                            checked = isBypassed,
                            onCheckedChange = { viewModel.toggleTimeLockBypass() }
                        )
                    }
                }
            }
        }

        // Scanner Viewfinder
        item {
            if (!isSessionActive) {
                // Locked View
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEE2E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Absensi ${currentSession.name} Terkunci",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Absensi hanya dapat dilakukan pada waktu yang telah ditentukan:\n${currentSession.getEffectiveTimeRange(teacherUnitSchedule)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.toggleTimeLockBypass() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Buka Kunci untuk Pengujian (Demo Mode)")
                        }
                    }
                }
            } else {
                ScannerCameraView(
                    currentSession = currentSession,
                    onBarcodeScanned = { code -> viewModel.processBarcode(code) }
                )
            }
        }

        // Live Scan Feedback Banner (Card with WhatsApp Button)
        item {
            ScanResultCard(
                feedback = feedback,
                onDismiss = { viewModel.clearFeedback() }
            )
        }

        // Recent Scans Section Header (Unit Mereka)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kehadiran ${currentSession.name} (${sessionRecords.size} Santri Unit ${currentUser?.unitPendidikan ?: ""})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A)
                    )
                }
            }
        }

        // List of Scanned Students for Current Session
        if (sessionRecords.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Belum ada santri Unit ${currentUser?.unitPendidikan ?: ""} yang di-scan pada sesi ini.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        } else {
            items(sessionRecords, key = { it.id }) { record ->
                AttendanceItemCard(
                    record = record,
                    onSendWhatsApp = {
                        val msg = WhatsAppHelper.buildAttendanceMessage(record)
                        WhatsAppHelper.openWhatsAppChat(context, record.parentPhone, msg)
                    }
                )
            }
        }
    }
}

@Composable
private fun AttendanceItemCard(
    record: AttendanceRecord,
    onSendWhatsApp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (record.status == "Hadir") Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = record.studentName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (record.status == "Hadir") Color(0xFF16A34A) else Color(0xFFD97706)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.studentName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = record.unitPendidikan,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        text = "ID: ${record.studentId} • Kelas: ${record.className} • ${record.dormitory}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "Waktu: ${record.time} WIB • Petugas: ${record.scannedBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(
                onClick = onSendWhatsApp,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFF25D366).copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Kirim WhatsApp",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
