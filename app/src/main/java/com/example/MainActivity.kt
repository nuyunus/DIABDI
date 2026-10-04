package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UserRole
import com.example.ui.SantriViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ParentHomeScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StudentsScreen
import com.example.ui.screens.SuperAdminDashboardScreen
import com.example.ui.screens.UnitAdminScreen
import com.example.ui.theme.SantriScanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SantriScanTheme {
                val app = application as SantriApp
                val viewModel: SantriViewModel = viewModel(
                    factory = SantriViewModel.provideFactory(app)
                )

                RootAppNavigation(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootAppNavigation(viewModel: SantriViewModel) {
    var showSplash by rememberSaveable { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onTimeout = { showSplash = false })
        return
    }

    val currentUser by viewModel.currentUser.collectAsState()

    if (currentUser == null) {
        // Layar Login Satu Pintu (Single Page Login)
        LoginScreen(viewModel = viewModel)
        return
    }

    // Berdasarkan Role yang Login (Super Admin, Admin Unit, Guru, atau Santri/Wali)
    when (currentUser?.role) {
        UserRole.SUPER_ADMIN -> {
            // Panel Khusus Super Admin: Otoritas Penuh Semua Unit & Sinkronisasi Database
            SuperAdminDashboardScreen(
                viewModel = viewModel,
                onLogout = { viewModel.logout() }
            )
        }

        UserRole.ADMIN -> {
            // Panel Khusus Admin Unit (MTs, MA, SMK): Otoritas Sesuai Unit Masing-masing
            UnitAdminScreen(
                viewModel = viewModel,
                onLogout = { viewModel.logout() }
            )
        }

        UserRole.WALI_SANTRI -> {
            // Versi Mobile Khusus Santri / Orang Tua
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "DIABDI (Portal Santri)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = currentUser?.displayName ?: "Santri",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.logout() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Logout",
                                    tint = Color(0xFFDC2626)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) { padding ->
                ParentHomeScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
            }
        }

        UserRole.GURU -> {
            // Versi Mobile Khusus Guru Piket (Presensi Scan QR & Data Santri Unit)
            var currentGuruTab by remember { mutableIntStateOf(0) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = currentUser?.displayName ?: "Guru Piket",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "DIABDI Guru • Unit ${currentUser?.unitPendidikan ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = currentUser?.unitPendidikan ?: "",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.logout() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Logout",
                                    tint = Color(0xFFDC2626)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.White,
                        modifier = Modifier.testTag("teacher_bottom_nav")
                    ) {
                        NavigationBarItem(
                            selected = currentGuruTab == 0,
                            onClick = { currentGuruTab = 0 },
                            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR") },
                            label = { Text("Scan QR") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                        NavigationBarItem(
                            selected = currentGuruTab == 1,
                            onClick = { currentGuruTab = 1 },
                            icon = { Icon(Icons.Default.People, contentDescription = "Santri") },
                            label = { Text("Data Santri") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { padding ->
                val modifier = Modifier.padding(padding)
                when (currentGuruTab) {
                    0 -> ScanScreen(viewModel = viewModel, modifier = modifier)
                    1 -> StudentsScreen(
                        viewModel = viewModel,
                        onNavigateToAi = { student ->
                            viewModel.selectStudentForAi(student)
                        },
                        modifier = modifier
                    )
                }
            }
        }
        null -> {}
    }
}
