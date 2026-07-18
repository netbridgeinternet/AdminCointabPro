package com.urbancointabpro.admin.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.urbancointabpro.admin.drive.DriveManager
import com.urbancointabpro.admin.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveFolderQrScreen(
    driveManager: DriveManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var folderUrl by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    // Generate QR when URL changes
    fun generateQr(url: String) {
        if (url.isBlank()) {
            qrBitmap = null
            return
        }
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(url.trim(), BarcodeFormat.QR_CODE, 512, 512)
            val size = bitMatrix.width
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            qrBitmap = bitmap
            errorMessage = ""
        } catch (e: Exception) {
            errorMessage = "Failed to generate QR: ${e.message}"
            qrBitmap = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Drive Folder QR", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Generate QR code for kiosk to scan", fontSize = 12.sp, color = TextSecondary)
                    }
                },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Service Account Info Card ────────────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share your Drive folder with this email first:", fontSize = 12.sp, color = Color(0xFF64B5F6), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1B2A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF64B5F6).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            DriveManager.SERVICE_ACCOUNT_EMAIL,
                            fontSize = 11.sp,
                            color = Color(0xFF64B5F6),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Service Account Email", DriveManager.SERVICE_ACCOUNT_EMAIL))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, "Copy email", tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ── URL Input Card ───────────────────────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Link, null, tint = Accent, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PASTE DRIVE FOLDER URL", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                            Text("Copy from your browser address bar", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = folderUrl,
                        onValueChange = {
                            folderUrl = it
                            errorMessage = ""
                            if (it.isNotBlank() && it.contains("drive.google.com")) {
                                generateQr(it)
                            } else {
                                qrBitmap = null
                            }
                        },
                        label = { Text("Google Drive folder URL") },
                        placeholder = { Text("https://drive.google.com/drive/folders/...") },
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
                            if (folderUrl.isNotEmpty()) {
                                IconButton(onClick = {
                                    folderUrl = ""
                                    qrBitmap = null
                                    errorMessage = ""
                                }) {
                                    Icon(Icons.Filled.Clear, null, tint = TextSecondary)
                                }
                            }
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { generateQr(folderUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        enabled = folderUrl.isNotBlank()
                    ) {
                        Icon(Icons.Filled.QrCode2, null)
                        Spacer(Modifier.width(8.dp))
                        Text("GENERATE QR CODE", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── QR Code Display ─────────────────────────────────────
            if (qrBitmap != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.QrCode2, null, tint = AccentGreen, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SCAN THIS QR ON THE KIOSK", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Open the kiosk → Cloud tab → SCAN QR CODE", fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)

                        Spacer(Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(2.dp, AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "Drive Folder QR Code",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // URL preview
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D1B2A), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                folderUrl.take(60) + if (folderUrl.length > 60) "..." else "",
                                fontSize = 9.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Drive URL", folderUrl))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, "Copy URL", tint = TextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // ── Error Message ────────────────────────────────────────
            if (errorMessage.isNotBlank()) {
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
                        Text(errorMessage, fontSize = 12.sp, color = Color(0xFFCCCCCC))
                    }
                }
            }

            // ── Step-by-step Instructions ────────────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.HelpOutline, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("How it works", fontWeight = FontWeight.SemiBold, color = Color(0xFF64B5F6), fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    val steps = listOf(
                        "Create a folder in your Google Drive (e.g., \"Cointab Captures\")",
                        "Right-click the folder → Share → add the service account email above as Editor",
                        "Uncheck \"Notify people\" → click Share",
                        "Copy the folder URL from your browser address bar",
                        "Paste the URL above → click Generate QR Code",
                        "On the kiosk tablet → Cloud tab → tap SCAN QR CODE",
                        "Point the kiosk camera at this QR code → auto-connected!"
                    )
                    steps.forEachIndexed { i, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${i + 1}. ", fontSize = 12.sp, color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(step, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                        }
                        if (i < steps.size - 1) Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
