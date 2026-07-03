package com.urbancointabpro.admin.ui.screens

import android.util.Log
import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbancointabpro.admin.drive.DriveConsentRequiredException
import com.urbancointabpro.admin.drive.DriveManager
import com.urbancointabpro.admin.pairing.PairingQRData
import com.urbancointabpro.admin.pairing.QRPairingManager
import com.urbancointabpro.admin.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun SetupScreen(
    driveManager: DriveManager,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folderPath by remember { mutableStateOf("Setting up...") }
    var folderId by remember { mutableStateOf("") }
    var folderUrl by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSettingUp by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var folderCreated by remember { mutableStateOf(false) }

    // State to hold a pending consent intent that needs to be launched
    var pendingConsentIntent by remember { mutableStateOf<android.content.Intent?>(null) }

    // Launcher for OAuth consent screen — Google requires this on first Drive API access
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // After user grants/denies consent, retry the setup
        if (result.resultCode == Activity.RESULT_OK) {
            // User granted consent — retry setup
            isSettingUp = true
            error = null
            scope.launch {
                try {
                    withTimeout(60_000L) {
                        val id = driveManager.ensureRootFolder()
                        val path = driveManager.getRootFolderPath()
                        val url = driveManager.getFolderWebUrl(id) ?: ""
                        val pairingData = driveManager.getPairingData()
                        val qrManager = QRPairingManager()
                        val qr = qrManager.generateQRBitmap(pairingData)
                        folderPath = path
                        folderId = id
                        folderUrl = url
                        qrBitmap = qr
                        folderCreated = true
                        isSettingUp = false
                    }
                } catch (e: DriveConsentRequiredException) {
                    // Consent needed again (unusual but possible)
                    pendingConsentIntent = e.consentIntent
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    error = "Setup timed out. Please check your internet connection and try again."
                    isSettingUp = false
                } catch (e: Exception) {
                    Log.e("SetupScreen", "Setup failed", e)
                    error = e.message ?: "${e.javaClass.simpleName}: (no message)"
                    isSettingUp = false
                }
            }
        } else {
            // User denied consent
            error = "Google Drive permission was denied. Please grant access to use Drive storage."
            isSettingUp = false
        }
    }

    // Launch consent intent when pendingConsentIntent changes
    LaunchedEffect(pendingConsentIntent) {
        pendingConsentIntent?.let { intent ->
            pendingConsentIntent = null  // Clear to avoid re-triggering
            consentLauncher.launch(intent)
        }
    }

    // Main setup logic — runs on first composition
    LaunchedEffect(Unit) {
        try {
            withTimeout(60_000L) {
                val id = driveManager.ensureRootFolder()
                val path = driveManager.getRootFolderPath()
                val url = driveManager.getFolderWebUrl(id) ?: ""
                val pairingData = driveManager.getPairingData()
                val qrManager = QRPairingManager()
                val qr = qrManager.generateQRBitmap(pairingData)
                folderPath = path
                folderId = id
                folderUrl = url
                qrBitmap = qr
                folderCreated = true
                isSettingUp = false
            }
        } catch (e: DriveConsentRequiredException) {
            // Google needs OAuth consent — trigger the consent screen via state
            pendingConsentIntent = e.consentIntent
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            error = "Setup timed out. Please check your internet connection and try again."
            isSettingUp = false
        } catch (e: Exception) {
            Log.e("SetupScreen", "Setup failed", e)
            error = e.message ?: "${e.javaClass.simpleName}: (no message)"
            isSettingUp = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                "Setup Complete",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(32.dp))

            if (isSettingUp) {
                CircularProgressIndicator(color = Accent)
                Spacer(Modifier.height(16.dp))
                Text("Creating your Google Drive folder...", color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Text("This may take a moment on first launch.", fontSize = 12.sp, color = TextSecondary)
            } else if (error != null) {
                Icon(Icons.Filled.Error, null, tint = AccentRed, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Error: $error", color = AccentRed, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        isSettingUp = true
                        error = null
                        scope.launch {
                            try {
                                withTimeout(60_000L) {
                                    val id = driveManager.ensureRootFolder()
                                    val path = driveManager.getRootFolderPath()
                                    val url = driveManager.getFolderWebUrl(id) ?: ""
                                    val pairingData = driveManager.getPairingData()
                                    val qrManager = QRPairingManager()
                                    val qr = qrManager.generateQRBitmap(pairingData)
                                    folderPath = path
                                    folderId = id
                                    folderUrl = url
                                    qrBitmap = qr
                                    folderCreated = true
                                    isSettingUp = false
                                }
                            } catch (e: DriveConsentRequiredException) {
                                pendingConsentIntent = e.consentIntent
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                error = "Setup timed out. Please check your internet connection and try again."
                                isSettingUp = false
                            } catch (e: Exception) {
                                error = e.message ?: "${e.javaClass.simpleName}: (no message)"
                                isSettingUp = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                ) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry")
                }
            } else {
                // ── Storage path card (CLICKABLE) ──────────────────
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (folderId.isNotBlank()) {
                                try {
                                    val intent = driveManager.getOpenFolderIntent(folderId)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open Drive: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (folderCreated) Icons.Filled.Folder else Icons.Filled.FolderOff,
                                null,
                                tint = if (folderCreated) Accent else AccentRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Storage Path", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                if (folderCreated) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(folderPath, fontSize = 12.sp, color = TextSecondary)
                            if (folderCreated) {
                                Spacer(Modifier.height(2.dp))
                                Text("Tap to open in Google Drive", fontSize = 10.sp, color = Accent)
                            }
                        }
                        Icon(
                            Icons.Filled.OpenInNew,
                            null,
                            tint = Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // ── Folder ID card ─────────────────────────────────
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Key, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Folder ID", fontSize = 10.sp, color = TextSecondary)
                            Text(folderId, fontSize = 11.sp, color = Color(0xFF64B5F6), fontFamily = FontFamily.Monospace)
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Folder ID", folderId))
                                Toast.makeText(context, "Folder ID copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Filled.ContentCopy, "Copy", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // ── QR Code for pairing ────────────────────────────
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.QrCode2, null, tint = Accent, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Legacy Pairing QR", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("For kiosks that scan admin QR codes", fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.height(16.dp))

                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Pairing QR Code",
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // ── Service account email sharing ──────────────────
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AddCircle, null, tint = AccentGreen)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Add Service Account", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                                Text("Share folder with a service account email", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))

                // ── Continue button ────────────────────────────────
                Button(
                    onClick = onSetupComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Go to Dashboard", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
