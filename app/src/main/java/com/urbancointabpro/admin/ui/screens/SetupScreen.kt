package com.urbancointabpro.admin.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbancointabpro.admin.drive.DriveManager
import com.urbancointabpro.admin.pairing.PairingQRData
import com.urbancointabpro.admin.pairing.QRPairingManager
import com.urbancointabpro.admin.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(
    driveManager: DriveManager,
    onSetupComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var folderPath by remember { mutableStateOf("Setting up...") }
    var folderId by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSettingUp by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            folderId = driveManager.ensureRootFolder()
            folderPath = driveManager.getRootFolderPath()

            // Generate QR code
            val pairingData = driveManager.getPairingData()
            val qrManager = QRPairingManager()
            qrBitmap = qrManager.generateQRBitmap(pairingData)

            isSettingUp = false
        } catch (e: Exception) {
            error = e.message
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
            } else if (error != null) {
                Icon(Icons.Filled.Error, null, tint = AccentRed, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Error: $error", color = AccentRed)
            } else {
                // Storage path card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Folder, null, tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Storage Path", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            Text(folderPath, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                // QR Code for pairing
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
                        Text("Pair a Device", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Scan this QR code on your CointabPro kiosk", fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.height(16.dp))

                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Pairing QR Code",
                                modifier = Modifier.size(240.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Folder ID: $folderId", fontSize = 10.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(24.dp))

                // Service account email sharing
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

                // Continue button
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
