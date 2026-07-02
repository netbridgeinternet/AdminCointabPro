package com.urbancointabpro.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbancointabpro.admin.drive.DriveManager
import com.urbancointabpro.admin.service.DriveSyncService
import com.urbancointabpro.admin.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    driveManager: DriveManager,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accountEmail = driveManager.getAccountEmail()
    var syncInterval by remember { mutableStateOf(30L) }
    var serviceAccountMode by remember { mutableStateOf(false) }
    var serviceAccountEmail by remember { mutableStateOf("") }
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Primary)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Account info card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AccountCircle, null, tint = Accent, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(accountEmail ?: "Unknown", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(accountEmail ?: "", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }

            // Sync frequency
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sync, null, tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sync Frequency", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("How often to check for new photos", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Every", fontSize = 14.sp, color = TextSecondary)
                        Spacer(Modifier.width(8.dp))
                        val intervals = listOf(15L, 30L, 60L, 120L, 300L)
                        intervals.forEach { interval ->
                            FilterChip(
                                selected = syncInterval == interval,
                                onClick = {
                                    syncInterval = interval
                                    val intent = android.content.Intent(context, DriveSyncService::class.java)
                                    intent.action = DriveSyncService.ACTION_START
                                    intent.putExtra(DriveSyncService.EXTRA_INTERVAL_SECONDS, interval)
                                    context.startService(intent)
                                },
                                label = {
                                    Text(
                                        if (interval < 60) "${interval}s" else "${interval / 60}m",
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Primary
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }
            }

            // Service Account Mode
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.VpnKey, null, tint = if (serviceAccountMode) AccentGreen else TextSecondary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Service Account Mode", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Use a service account for kiosk uploads", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = serviceAccountMode,
                        onCheckedChange = { serviceAccountMode = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
                    )
                }
                if (serviceAccountMode) {
                    HorizontalDivider(color = Color(0xFF333333))
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = serviceAccountEmail,
                            onValueChange = { serviceAccountEmail = it },
                            label = { Text("Service Account Email") },
                            placeholder = { Text("kiosk@your-project.iam.gserviceaccount.com") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Color(0xFF444444)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                // Share folder with service account
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                        ) {
                            Icon(Icons.Filled.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Share Folder")
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Sign out
            Button(
                onClick = { showSignOutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Sign out confirmation dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out?") },
            text = { Text("You'll need to sign in again to access your Drive folder and device photos.") },
            confirmButton = {
                TextButton(onClick = {
                    driveManager.signOut()
                    onSignOut()
                }) { Text("Sign Out", color = AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            }
        )
    }
}
