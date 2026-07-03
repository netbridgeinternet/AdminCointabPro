package com.urbancointabpro.admin.ui.screens

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbancointabpro.admin.drive.DriveManager
import com.urbancointabpro.admin.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(
    driveManager: DriveManager,
    onSignedIn: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    // Step 1: Account picker launcher
    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val accountName = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (accountName != null) {
                // Account picked — now request Drive consent
                statusMessage = "Requesting Drive access..."
                scope.launch {
                    try {
                        val consentIntent = driveManager.requestConsentIfNeeded(accountName)
                        if (consentIntent != null) {
                            // Consent is needed — launch the consent screen
                            consentLauncher.launch(consentIntent)
                        } else {
                            // Consent already granted — initialize Drive and proceed
                            driveManager.initializeDrive(accountName)
                            Toast.makeText(context, "Signed in as $accountName", Toast.LENGTH_SHORT).show()
                            onSignedIn()
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        statusMessage = ""
                        val errorMsg = e.message ?: "${e.javaClass.simpleName}: (no detail)"
                        Log.e("SignInScreen", "Consent request failed", e)
                        Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                isLoading = false
                statusMessage = ""
                Toast.makeText(context, "No account selected", Toast.LENGTH_SHORT).show()
            }
        } else {
            isLoading = false
            statusMessage = ""
            Toast.makeText(context, "Sign-in cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Step 2: OAuth consent launcher
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // After consent screen, try requesting consent again to confirm it was granted
        val savedAccount = driveManager.getAccountEmail()
        if (savedAccount != null) {
            scope.launch {
                try {
                    val consentIntent = driveManager.requestConsentIfNeeded(savedAccount)
                    if (consentIntent != null) {
                        // Still needs consent — this shouldn't normally happen
                        isLoading = false
                        statusMessage = ""
                        Toast.makeText(context, "Drive permission still needed. Please try again.", Toast.LENGTH_LONG).show()
                    } else {
                        // Consent granted — initialize Drive and proceed
                        driveManager.initializeDrive(savedAccount)
                        isLoading = false
                        statusMessage = ""
                        Toast.makeText(context, "Drive access granted!", Toast.LENGTH_SHORT).show()
                        onSignedIn()
                    }
                } catch (e: Exception) {
                    isLoading = false
                    statusMessage = ""
                    val errorMsg = e.message ?: "${e.javaClass.simpleName}: (no detail)"
                    Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            isLoading = false
            statusMessage = ""
            Toast.makeText(context, "No account found. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo / Title
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = "Admin",
                tint = Accent,
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Admin CointabPro",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Monitor your kiosk devices",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(48.dp))

            // Google Sign-In Button
            Button(
                onClick = {
                    isLoading = true
                    statusMessage = "Opening account picker..."
                    accountPickerLauncher.launch(driveManager.getAccountPickerIntent())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueGmail),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    "Sign in with Google",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Status message
            if (statusMessage.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    statusMessage,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Your Google Drive will be used to store\nphotos from your kiosk devices.",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
