package com.urbancointabpro.admin.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.activity.compose.BackHandler
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.urbancointabpro.admin.drive.DriveManager
import com.urbancointabpro.admin.ui.screens.*
import com.urbancointabpro.admin.ui.theme.AdminCointabProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdminCointabProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AdminApp()
                }
            }
        }
    }
}

@Composable
fun AdminApp() {
    val navController = rememberNavController()
    val driveManager = remember { DriveManager(navController.context) }

    // Track current route to handle back navigation properly
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Root screens — pressing back should NOT close the app
    // Instead, show a confirmation or do nothing
    val rootRoutes = setOf("signin", "setup", "dashboard")

    BackHandler(enabled = currentRoute in rootRoutes) {
        // On root screens, don't close the app on back press
        // Move to background instead (like home button)
        // This prevents accidental app closure
    }

    // Auto-initialize Drive if account was previously saved
    LaunchedEffect(Unit) {
        if (driveManager.wasPreviouslySignedIn() && !driveManager.isInitialized()) {
            driveManager.initializeDriveFromSaved()
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (driveManager.wasPreviouslySignedIn() && driveManager.isInitialized()) "dashboard" else "signin"
    ) {
        composable("signin") {
            SignInScreen(
                driveManager = driveManager,
                onSignedIn = {
                    navController.navigate("setup") {
                        popUpTo("signin") { inclusive = true }
                    }
                }
            )
        }
        composable("setup") {
            SetupScreen(
                driveManager = driveManager,
                onSetupComplete = {
                    navController.navigate("dashboard") {
                        popUpTo("setup") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                driveManager = driveManager,
                onDeviceClick = { deviceId, deviceName ->
                    navController.navigate("device/$deviceId/$deviceName")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onPairDeviceClick = {
                    navController.navigate("pair_device")
                },
                onDriveQrClick = {
                    navController.navigate("drive_qr")
                }
            )
        }
        composable("drive_qr") {
            DriveFolderQrScreen(
                driveManager = driveManager,
                onBack = { navController.popBackStack() }
            )
        }
        composable("pair_device") {
            PairDeviceScreen(
                driveManager = driveManager,
                onBack = { navController.popBackStack() },
                onDevicePaired = { folderId, deviceName ->
                    navController.navigate("device/$folderId/$deviceName") {
                        popUpTo("dashboard")
                    }
                }
            )
        }
        composable("device/{folderId}/{deviceName}") { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
            val deviceName = backStackEntry.arguments?.getString("deviceName") ?: "Unknown"
            DeviceDetailScreen(
                driveManager = driveManager,
                deviceFolderId = folderId,
                deviceName = deviceName,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                driveManager = driveManager,
                onSignOut = {
                    navController.navigate("signin") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
