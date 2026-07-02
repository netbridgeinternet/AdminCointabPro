package com.urbancointabpro.admin.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbancointabpro.admin.drive.DriveManager
import com.urbancointabpro.admin.drive.PhotoInfo
import com.urbancointabpro.admin.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    driveManager: DriveManager,
    deviceFolderId: String,
    deviceName: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<PhotoInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(deviceFolderId) {
        scope.launch {
            try {
                photos = driveManager.listPhotos(deviceFolderId)
            } catch (e: Exception) {
                // Handle error
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(deviceName, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("${photos.size} photos", fontSize = 12.sp, color = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                }
            )
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
            } else if (photos.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.PhotoCamera, null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No photos yet", color = TextSecondary, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Photos will appear here when the kiosk captures them", color = TextSecondary, fontSize = 12.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(photos) { photo ->
                        PhotoThumbnail(
                            photo = photo,
                            driveManager = driveManager
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(
    photo: PhotoInfo,
    driveManager: DriveManager
) {
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(photo.id) {
        scope.launch {
            val bytes = driveManager.downloadPhotoBytes(photo.id)
            bytes?.let {
                bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = photo.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: CircularProgressIndicator(
            color = Accent,
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
    }
}
