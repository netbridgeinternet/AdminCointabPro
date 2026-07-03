package com.urbancointabpro.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.urbancointabpro.admin.drive.DeviceInfo
import com.urbancointabpro.admin.drive.DriveManager
import com.urbancointabpro.admin.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    driveManager: DriveManager,
    onDeviceClick: (String, String) -> Unit,
    onSettingsClick: () -> Unit,
    onPairDeviceClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalPhotos by remember { mutableStateOf(0) }

    // Refresh devices
    fun refresh() {
        scope.launch {
            isLoading = true
            try {
                driveManager.ensureRootFolder()
                devices = driveManager.listDevices()
                totalPhotos = driveManager.getTotalPhotoCount()
            } catch (e: Exception) {
                // Handle error
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Auto-refresh every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Admin CointabPro", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("$totalPhotos photos • ${devices.size} devices", fontSize = 12.sp, color = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary),
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = TextPrimary)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, "Settings", tint = TextPrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onPairDeviceClick,
                containerColor = Accent,
                contentColor = Primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, "Pair Device")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Primary)
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Accent,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (devices.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Devices,
                        null,
                        tint = TextSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No devices paired yet", color = TextSecondary, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap + to pair a kiosk device", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = onPairDeviceClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pair a Device")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceClick(device.folderId, device.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceInfo,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device icon or last photo thumbnail
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.TabletAndroid,
                    null,
                    tint = Accent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${device.photoCount} photo${if (device.photoCount != 1) "s" else ""}", fontSize = 12.sp, color = TextSecondary)
                }
                device.lastPhotoTimestamp?.let { ts ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Last: ${formatTimestamp(ts)}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            // Status indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(AccentGreen)
                )
                Spacer(Modifier.height(4.dp))
                Text("Active", fontSize = 10.sp, color = AccentGreen)
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
