package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.data.remote.GoogleSheetsSyncService
import com.example.ui.SantriViewModel

/**
 * Menu Absensi Manual:
 * Form input:
 * 1. Pilih Sesi (7 Sesi Harian)
 * 2. Pilih Kelas
 * 3. Pilih Keterangan (Hadir, Sakit Asrama, Sakit Pulang, Izin Pulang, Alpa)
 * 4. Pilih Nama Siswa (Checkbox multi-select agar bisa memilih lebih dari 1 siswa)
 * 5. Tulis Keterangan / Catatan
 * 6. Hubungan realtime dengan Google Sheets & Web Apps Vercel
 */
@Composable
fun ManualAttendanceScreen(
    viewModel: SantriViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val allStudents by viewModel.studentsList.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val sessionRecords by viewModel.currentSessionRecords.collectAsState()
    val isRecordingBulk by viewModel.isRecordingBulk.collectAsState()
    val bulkRecordResult by viewModel.bulkRecordResult.collectAsState()

    // 1. Filter Siswa Berdasarkan Unit Guru (Jika Guru Login)
    val unitFilter = currentUser?.unitPendidikan
    val baseStudents = remember(allStudents, unitFilter) {
        if (unitFilter.isNullOrBlank() || unitFilter.equals("Semua", ignoreCase = true) || unitFilter.equals("ALL", ignoreCase = true)) {
            allStudents
        } else {
            allStudents.filter { it.unitPendidikan.equals(unitFilter, ignoreCase = true) }
        }
    }

    // 2. Daftar Kelas Unik yang Tersedia
    val availableClasses = remember(baseStudents) {
        baseStudents.map { it.className.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    var selectedClass by remember(availableClasses) {
        mutableStateOf(availableClasses.firstOrNull() ?: "")
    }

    // 3. Status Absensi (5 Pilihan Sesuai Permintaan)
    val statusOptions = listOf(
        "Hadir" to Color(0xFF16A34A),
        "Sakit Asrama" to Color(0xFFEA580C),
        "Sakit Pulang" to Color(0xFFD97706),
        "Izin Pulang" to Color(0xFF2563EB),
        "Alpa" to Color(0xFFDC2626)
    )
    var selectedStatus by remember { mutableStateOf("Hadir") }

    // 4. Catatan Keterangan
    var notesText by remember { mutableStateOf("") }

    // 5. Pencarian Nama Siswa
    var searchQuery by remember { mutableStateOf("") }

    // 6. Multi-select Checkboxes untuk Nama Siswa
    val selectedStudentIds = remember { mutableStateListOf<String>() }

    // Siswa yang difilter berdasarkan kelas dan pencarian
    val displayedStudents = remember(baseStudents, selectedClass, searchQuery) {
        baseStudents.filter { student ->
            val matchClass = if (selectedClass.isBlank() || selectedClass == "Semua") true else student.className.equals(selectedClass, ignoreCase = true)
            val matchQuery = if (searchQuery.isBlank()) true else {
                student.name.contains(searchQuery, ignoreCase = true) ||
                student.id.contains(searchQuery, ignoreCase = true) ||
                student.nisn.contains(searchQuery, ignoreCase = true)
            }
            matchClass && matchQuery
        }
    }

    // Map ID Santri ke status tercatat hari ini pada sesi aktif
    val recordedMap = remember(sessionRecords) {
        sessionRecords.associateBy({ it.studentId }, { it.status to it.time })
    }

    val isAllSelected by remember(displayedStudents, selectedStudentIds) {
        derivedStateOf {
            displayedStudents.isNotEmpty() && displayedStudents.all { selectedStudentIds.contains(it.id) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Banner Header & Link Realtime ke Web Apps Vercel
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF047857)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AssignmentTurnedIn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Form Absensi Manual",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Sinkronisasi Realtime Android & Web Apps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFFA7F3D0),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Web Portal: absen-...vercel.app",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Vercel Web App", GoogleSheetsSyncService.VERCEL_WEB_APP_URL))
                                        Toast.makeText(context, "Link Web Apps berhasil disalin!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Salin Link Web Apps",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GoogleSheetsSyncService.VERCEL_WEB_APP_URL))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Tidak dapat membuka browser", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.OpenInBrowser,
                                        contentDescription = "Buka Web Apps di Browser",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. PILIH SESI ABSENSI
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "1. Pilih Sesi Absensi",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AttendanceSession.ALL_SESSIONS.forEach { session ->
                            val isSelected = session.id == currentSession.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectSession(session) },
                                label = {
                                    Text(
                                        text = session.name,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. PILIH KELAS
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "2. Pilih Kelas",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                        if (unitFilter != null && !unitFilter.equals("Semua", ignoreCase = true)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Unit $unitFilter",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (availableClasses.isEmpty()) {
                        Text(
                            text = "Belum ada kelas terdaftar. Tarik data siswa dari Google Sheets terlebih dahulu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Opsi Semua Kelas
                            FilterChip(
                                selected = selectedClass == "Semua",
                                onClick = {
                                    selectedClass = "Semua"
                                    selectedStudentIds.clear()
                                },
                                label = { Text("Semua Kelas") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            availableClasses.forEach { cls ->
                                val isSelected = selectedClass == cls
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedClass = cls
                                        selectedStudentIds.clear()
                                    },
                                    label = { Text("Kelas $cls") },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. PILIH KETERANGAN STATUS (Hadir, Sakit Asrama, Sakit Pulang, Izin Pulang, Alpa)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "3. Pilih Keterangan Absensi",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Pilih salah satu status yang akan diterapkan pada siswa terpilih:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        statusOptions.forEach { (statusName, statusColor) ->
                            val isSelected = selectedStatus == statusName
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) statusColor else statusColor.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) statusColor else statusColor.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedStatus = statusName }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = statusName,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) Color.White else statusColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. TULIS KETERANGAN (CATATAN)
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Tulis Keterangan (Opsional)") },
                        placeholder = { Text("Contoh: Flu & batuk istirahat di asrama, Izin ada acara keluarga, dll.") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_attendance_notes_input")
                    )
                }
            }
        }

        // 5. PILIH NAMA SISWA DENGAN CHECKBOX (BISA MULTI-SELECT)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "4. Pilih Nama Siswa",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "${selectedStudentIds.size} dari ${displayedStudents.size} santri dipilih",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedStudentIds.isNotEmpty()) MaterialTheme.colorScheme.primary else Color(0xFF64748B)
                            )
                        }

                        // Tombol Pilih Semua / Batal Pilih
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isAllSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        displayedStudents.forEach { st ->
                                            if (!selectedStudentIds.contains(st.id)) {
                                                selectedStudentIds.add(st.id)
                                            }
                                        }
                                    } else {
                                        displayedStudents.forEach { st ->
                                            selectedStudentIds.remove(st.id)
                                        }
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = if (isAllSelected) "Batal Semua" else "Pilih Semua",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    if (isAllSelected) {
                                        displayedStudents.forEach { st -> selectedStudentIds.remove(st.id) }
                                    } else {
                                        displayedStudents.forEach { st ->
                                            if (!selectedStudentIds.contains(st.id)) selectedStudentIds.add(st.id)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Kolom Pencarian Siswa
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari nama siswa atau ID santri...", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Pesan Sukses / Hasil Simpan
        if (bulkRecordResult != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFECFDF5),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6EE7B7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = bulkRecordResult ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF065F46),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Daftar Siswa Checkbox
        if (displayedStudents.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Tidak ada santri yang cocok dengan pencarian" else "Tidak ada santri di kelas $selectedClass",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        } else {
            items(displayedStudents, key = { it.id }) { student ->
                val isChecked = selectedStudentIds.contains(student.id)
                val recordedInfo = recordedMap[student.id]

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isChecked) 1.5.dp else 1.dp,
                        if (isChecked) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isChecked) selectedStudentIds.remove(student.id)
                            else selectedStudentIds.add(student.id)
                        }
                        .testTag("student_item_${student.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked) selectedStudentIds.add(student.id)
                                else selectedStudentIds.remove(student.id)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("checkbox_${student.id}")
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = student.name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = student.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFE2E8F0)
                                ) {
                                    Text(
                                        text = student.className,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF334155),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Text(
                                text = "ID: ${student.id} • Unit: ${student.unitPendidikan} • ${student.dormitory}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )

                            // Status jika sudah tercatat hari ini pada sesi aktif
                            if (recordedInfo != null) {
                                val (recStatus, recTime) = recordedInfo
                                Text(
                                    text = "Hari ini: $recStatus (Jam $recTime)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (recStatus == "Hadir") Color(0xFF16A34A) else Color(0xFFD97706)
                                )
                            }
                        }
                    }
                }
            }
        }

        // TOMBOL SIMPAN & SINKRONKAN ABSENSI MANUAL
        item {
            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    val targetStudents = baseStudents.filter { selectedStudentIds.contains(it.id) }
                    if (targetStudents.isEmpty()) {
                        Toast.makeText(context, "Silakan pilih setidaknya 1 santri!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    viewModel.recordBulkManualAttendance(
                        students = targetStudents,
                        session = currentSession,
                        status = selectedStatus,
                        notes = notesText
                    ) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success) {
                            selectedStudentIds.clear()
                            notesText = ""
                        }
                    }
                },
                enabled = selectedStudentIds.isNotEmpty() && !isRecordingBulk,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (selectedStatus) {
                        "Sakit Asrama" -> Color(0xFFEA580C)
                        "Sakit Pulang" -> Color(0xFFD97706)
                        "Izin Pulang" -> Color(0xFF2563EB)
                        "Alpa" -> Color(0xFFDC2626)
                        else -> MaterialTheme.colorScheme.primary
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_manual_attendance_button")
            ) {
                if (isRecordingBulk) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Menyimpan & Menyinkronkan...")
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedStudentIds.isEmpty()) "Pilih Santri Terlebih Dahulu"
                        else "Simpan Absensi ${selectedStudentIds.size} Santri ($selectedStatus)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tombol Pintas Buka Web Apps Absensi
            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GoogleSheetsSyncService.VERCEL_WEB_APP_URL))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Tidak dapat membuka browser", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buka Web Apps Absensi (Vercel)")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
