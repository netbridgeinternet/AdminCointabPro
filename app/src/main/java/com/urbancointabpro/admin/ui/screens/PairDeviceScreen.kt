package com.urbancointabpro.admin.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.integration.android.IntentIntegrator
import com.urbancointabpro.admin.drive.DriveManager
import com.urbancointabpro.admin.firestore.FirestoreHelper
import com.urbancointabpro.admin.pairing.KioskQRData
import com.urbancointabpro.admin.pairing.QRPairingManager
import com.urbancointabpro.admin.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairDeviceScreen(
    driveManager: DriveManager,
    onBack: () -> Unit,
    onDevicePaired: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val qrManager = remember { QRPairingManager() }
    val firestoreHelper = remember { FirestoreHelper() }

    var scannedDeviceId by remember { mutableStateOf<String?>(null) }
    var manualDeviceId by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }
    var pairingError by remember { mutableStateOf<String?>(null) }
    var pairingSuccess by remember { mutableStateOf(false) }
    var pairedFolderId by remember { mutableStateOf("") }
    var pairedFolderUrl by remember { mutableStateOf("") }
    var showManualEntry by remember { mutableStateOf(false) }

    // QR Scanner launcher
    val qrScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = IntentIntegrator.parseActivityResult(
                result.resultCode, result.data
            )
            val qrContent = scanResult?.contents
            if (qrContent.isNullOrBlank()) {
                pairingError = "No QR code detected"
                return@rememberLauncherForActivityResult
            }

            val kioskData = qrManager.parseKioskQR(qrContent)
            if (kioskData != null) {
                scannedDeviceId = kioskData.deviceId
                pairingError = null
            } else {
                pairingError = "Invalid QR code format. Expected kiosk pairing QR."
            }
        }
    }

    // Auto-pair when device ID is set (from scan)
    LaunchedEffect(scannedDeviceId) {
        val deviceId = scannedDeviceId ?: return@LaunchedEffect
        if (deviceId.isNotBlank() && !isPairing && !pairingSuccess) {
            isPairing = true
            pairingError = null
            try {
                val rootFolderId = driveManager.ensureRootFolder()
                val deviceFolderId = driveManager.createDeviceFolder(deviceId)
                val folderUrl = driveManager.getFolderWebUrl(deviceFolderId) ?: ""

                // Write pairing data to Firestore
                val adminEmail = driveManager.getAccountEmail() ?: ""
                val result = firestoreHelper.writePairingData(deviceId, deviceFolderId, adminEmail)

                if (result.isFailure) {
                    pairingError = "Drive folder created, but failed to sync to cloud: ${result.exceptionOrNull()?.message}"
                }

                pairedFolderId = deviceFolderId
                pairedFolderUrl = folderUrl
                pairingSuccess = true
            } catch (e: Exception) {
                pairingError = "Pairing failed: ${e.message}"
            }
            isPairing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair Device", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Success State ─────────────────────────────────────
            if (pairingSuccess && scannedDeviceId != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3A1B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            tint = AccentGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Device Paired!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Device ID: ${scannedDeviceId}",
                            fontSize = 14.sp,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Folder: Device-${scannedDeviceId}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(16.dp))

                        // Open Drive folder button
                        if (pairedFolderUrl.isNotBlank()) {
                            Button(
                                onClick = {
                                    val intent = driveManager.getOpenFolderIntent(pairedFolderId)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Icon(Icons.Filled.FolderOpen, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open in Google Drive", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // View photos button
                        OutlinedButton(
                            onClick = {
                                onDevicePaired(pairedFolderId, "Device-${scannedDeviceId}")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, null)
                            Spacer(Modifier.width(8.dp))
                            Text("View Photos")
                        }

                        Spacer(Modifier.height(12.dp))

                        // Pair another device
                        TextButton(
                            onClick = {
                                scannedDeviceId = null
                                pairingSuccess = false
                                pairedFolderId = ""
                                pairedFolderUrl = ""
                                pairingError = null
                            }
                        ) {
                            Icon(Icons.Filled.Add, null, tint = Accent)
                            Spacer(Modifier.width(4.dp))
                            Text("Pair Another Device", color = Accent)
                        }
                    }
                }
            } else {
                // ── Scan QR Card ────────────────────────────────────
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, null, tint = Accent, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "SCAN QR CODE",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "Point your camera at the kiosk QR code",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val integrator = IntentIntegrator(context as Activity)
                                integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                                integrator.setPrompt("Scan the QR code on the kiosk's Cloud tab")
                                integrator.setBeepEnabled(true)
                                integrator.setOrientationLocked(false)
                                qrScannerLauncher.launch(integrator.createScanIntent())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, null)
                            Spacer(Modifier.width(8.dp))
                            Text("SCAN DEVICE QR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                // ── Divider ────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
                    Text("  OR  ", fontSize = 12.sp, color = TextSecondary)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
                }

                // ── Manual Entry Card ───────────────────────────────
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showManualEntry = !showManualEntry }
                        ) {
                            Icon(Icons.Filled.Keyboard, null, tint = Accent, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "ENTER DEVICE ID",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "Type the Device ID from the kiosk screen",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                if (showManualEntry) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                null,
                                tint = TextSecondary
                            )
                        }

                        AnimatedVisibility(visible = showManualEntry) {
                            Column {
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = manualDeviceId,
                                    onValueChange = {
                                        manualDeviceId = it
                                        pairingError = null
                                    },
                                    label = { Text("Device ID") },
                                    placeholder = { Text("e.g. abc123def456") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = Accent,
                                        unfocusedBorderColor = Color(0xFF444444),
                                        cursorColor = Accent
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    trailingIcon = {
                                        if (manualDeviceId.isNotEmpty()) {
                                            IconButton(onClick = { manualDeviceId = "" }) {
                                                Icon(Icons.Filled.Clear, null, tint = TextSecondary)
                                            }
                                        }
                                    }
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val deviceId = manualDeviceId.trim()
                                        if (deviceId.isBlank()) {
                                            pairingError = "Please enter a Device ID"
                                            return@Button
                                        }
                                        scannedDeviceId = deviceId
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                    enabled = manualDeviceId.isNotBlank() && !isPairing
                                ) {
                                    Icon(Icons.Filled.Link, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("PAIR DEVICE", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // ── Loading State ──────────────────────────────────
                AnimatedVisibility(visible = isPairing) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Accent)
                            Spacer(Modifier.height(12.dp))
                            Text("Creating Drive folder & syncing...", color = TextSecondary)
                        }
                    }
                }

                // ── Error State ────────────────────────────────────
                AnimatedVisibility(visible = pairingError != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1B1B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Error, null, tint = AccentRed, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pairing Error", fontWeight = FontWeight.Bold, color = AccentRed, fontSize = 14.sp)
                                Text(pairingError ?: "", fontSize = 12.sp, color = Color(0xFFCCCCCC))
                            }
                        }
                    }
                }

                // ── Info Card ──────────────────────────────────────
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("How to pair a device", fontWeight = FontWeight.SemiBold, color = Color(0xFF64B5F6), fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "1. On the kiosk, open Admin Panel → Cloud tab\n" +
                            "2. Point your phone camera at the QR code\n" +
                            "3. Or copy the Device ID and enter it manually\n" +
                            "4. The kiosk will auto-detect the pairing via Firestore",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
