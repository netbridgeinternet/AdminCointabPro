package com.urbancointabpro.admin.drive

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.urbancointabpro.admin.pairing.PairingQRData
import java.util.Collections

data class DeviceInfo(
    val id: String,
    val name: String,
    val folderId: String,
    val photoCount: Int,
    val lastPhotoId: String?,
    val lastPhotoTimestamp: Long?
)

data class PhotoInfo(
    val id: String,
    val name: String,
    val timestamp: Long?,
    val thumbnailLink: String?,
    val webViewLink: String?
)

class DriveManager(private val context: Context) {
    companion object {
        private const val TAG = "DriveManager"
        const val ROOT_FOLDER_NAME = "CointabPro Photos"
        const val MIME_FOLDER = "application/vnd.google-apps.folder"
        const val MIME_IMAGE = "image/jpeg"
        const val PREFS_NAME = "admin_cointabpro_prefs"
        const val KEY_ACCOUNT_NAME = "account_name"
    }

    private var driveService: Drive? = null
    private var rootFolderId: String? = null
    private var lastChangeToken: String? = null
    private var credential: GoogleAccountCredential? = null
    private var accountName: String? = null

    /**
     * Get or create the GoogleAccountCredential for Drive API access.
     * This handles OAuth consent automatically — no Google Cloud Console
     * OAuth client configuration needed.
     */
    fun getCredential(): GoogleAccountCredential {
        if (credential == null) {
            credential = GoogleAccountCredential.usingOAuth2(
                context,
                Collections.singletonList(DriveScopes.DRIVE)
            )
            // Restore previously saved account
            val saved = getSavedAccountName()
            if (saved != null) {
                credential?.selectedAccountName = saved
                accountName = saved
            }
        }
        return credential!!
    }

    /**
     * Returns an Intent to launch the account picker.
     * This is GoogleAccountCredential's built-in picker — works without
     * any Google Cloud Console configuration.
     */
    fun getAccountPickerIntent(): Intent {
        return getCredential().newChooseAccountIntent()
    }

    /**
     * Initialize Drive service after account is selected.
     */
    fun initializeDrive(selectedAccountName: String) {
        accountName = selectedAccountName
        getCredential().selectedAccountName = selectedAccountName

        driveService = Drive.Builder(
            com.google.api.client.http.javanet.NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            getCredential()
        )
            .setApplicationName("Admin CointabPro")
            .build()

        // Save account for next launch
        saveAccountName(selectedAccountName)

        Log.i(TAG, "Drive service initialized for $selectedAccountName")
    }

    fun isInitialized(): Boolean = driveService != null

    /**
     * Initialize Drive from a previously saved account name.
     */
    fun initializeDriveFromSaved() {
        val saved = getSavedAccountName() ?: return
        initializeDrive(saved)
    }

    /**
     * Check if user was previously signed in (account saved in prefs).
     */
    fun wasPreviouslySignedIn(): Boolean {
        return getSavedAccountName() != null
    }

    /**
     * Get the signed-in account email.
     */
    fun getAccountEmail(): String? = accountName

    /**
     * Get the root folder ID.
     */
    fun getRootFolderId(): String? = rootFolderId

    /**
     * Sign out — clear saved account and Drive service.
     */
    fun signOut() {
        accountName = null
        driveService = null
        rootFolderId = null
        lastChangeToken = null
        credential = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACCOUNT_NAME)
            .apply()
        Log.i(TAG, "Signed out")
    }

    private fun saveAccountName(name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNT_NAME, name)
            .apply()
    }

    private fun getSavedAccountName(): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNT_NAME, null)
    }

    /**
     * Create the root "CointabPro Photos" folder if it doesn't exist.
     * Returns the folder ID.
     */
    suspend fun ensureRootFolder(): String = withContext(Dispatchers.IO) {
        val drive = driveService ?: throw IllegalStateException("Drive not initialized")

        // Check if folder already exists
        val existing = drive.files().list()
            .setQ("name='${ROOT_FOLDER_NAME}' and mimeType='${MIME_FOLDER}' and trashed=false and 'me' in owners")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        if (existing.files.isNotEmpty()) {
            rootFolderId = existing.files[0].id
            Log.i(TAG, "Root folder exists: $rootFolderId")

            // Ensure sharing is set up even for existing folders
            try {
                shareFolderForUpload(rootFolderId!!)
            } catch (e: Exception) {
                Log.w(TAG, "Folder may already be shared: ${e.message}")
            }

            return@withContext rootFolderId!!
        }

        // Create the folder
        val metadata = File().apply {
            name = ROOT_FOLDER_NAME
            mimeType = MIME_FOLDER
        }
        val folder = drive.files().create(metadata)
            .setFields("id, name, webViewLink")
            .execute()

        rootFolderId = folder.id
        Log.i(TAG, "Created root folder: $rootFolderId")

        // Share folder: allow anyone with link to upload
        shareFolderForUpload(rootFolderId!!)

        rootFolderId!!
    }

    /**
     * Share the folder so kiosks can upload photos.
     */
    private suspend fun shareFolderForUpload(folderId: String) = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext

        try {
            // Check if already shared
            val perms = drive.permissions().list(folderId)
                .setFields("permissions(id, type, role)")
                .execute()

            val alreadyShared = perms.permissions.any {
                it.type == "anyone" && it.role == "writer"
            }

            if (alreadyShared) {
                Log.i(TAG, "Folder already shared: anyone with link can upload")
                return@withContext
            }

            val permission = Permission().apply {
                type = "anyone"
                role = "writer"
                allowFileDiscovery = false
            }
            drive.permissions().create(folderId, permission)
                .setFields("id")
                .execute()
            Log.i(TAG, "Folder shared: anyone with link can upload")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share folder: ${e.message}")
        }
    }

    /**
     * Share folder with a specific email (for Service Account pairing).
     */
    suspend fun shareFolderWithEmail(folderId: String, email: String) = withContext(Dispatchers.IO) {
        val drive = driveService ?: throw IllegalStateException("Drive not initialized")

        val permission = Permission().apply {
            type = "user"
            role = "writer"
            emailAddress = email
        }
        drive.permissions().create(folderId, permission)
            .setSendNotificationEmail(true)
            .setEmailMessage("CointabPro Admin has shared a photo storage folder with you.")
            .setFields("id")
            .execute()
        Log.i(TAG, "Folder shared with: $email")
    }

    /**
     * Get the root folder path for display.
     */
    suspend fun getRootFolderPath(): String = withContext(Dispatchers.IO) {
        val folderId = rootFolderId ?: ensureRootFolder()
        val drive = driveService ?: return@withContext "Not connected"

        val email = accountName ?: "Unknown"
        "My Drive ($email) / $ROOT_FOLDER_NAME /"
    }

    /**
     * Create a device subfolder in the root folder.
     * Returns the device folder ID.
     */
    suspend fun createDeviceFolder(deviceId: String): String = withContext(Dispatchers.IO) {
        val rootId = rootFolderId ?: ensureRootFolder()
        val drive = driveService ?: throw IllegalStateException("Drive not initialized")

        val folderName = "Device-$deviceId"

        // Check if device folder already exists
        val existing = drive.files().list()
            .setQ("name='$folderName' and '$rootId' in parents and mimeType='$MIME_FOLDER' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        if (existing.files.isNotEmpty()) {
            val existingId = existing.files[0].id
            Log.i(TAG, "Device folder already exists: $existingId")
            return@withContext existingId
        }

        // Create the device subfolder
        val metadata = File().apply {
            name = folderName
            mimeType = MIME_FOLDER
            parents = listOf(rootId)
        }
        val folder = drive.files().create(metadata)
            .setFields("id, name, webViewLink")
            .execute()

        Log.i(TAG, "Created device folder: ${folder.id} ($folderName)")
        folder.id
    }

    /**
     * Get the web URL to open a Drive folder in browser.
     */
    suspend fun getFolderWebUrl(folderId: String): String? = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext null

        try {
            val file = drive.files().get(folderId)
                .setFields("webViewLink")
                .execute()
            file.webViewLink
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get folder URL: ${e.message}")
            // Fallback: construct URL manually
            "https://drive.google.com/drive/folders/$folderId"
        }
    }

    /**
     * Get an Intent to open a Drive folder in the Google Drive app or browser.
     */
    fun getOpenFolderIntent(folderId: String): Intent {
        // Try opening in Google Drive app first
        val driveIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://drive.google.com/drive/folders/$folderId")
            setPackage("com.google.android.apps.docs")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Check if Drive app is available
        val resolveInfo = context.packageManager.resolveActivity(driveIntent, 0)
        return if (resolveInfo != null) {
            driveIntent
        } else {
            // Fallback to browser
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://drive.google.com/drive/folders/$folderId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * List all device subfolders in the root folder.
     */
    suspend fun listDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        val folderId = rootFolderId ?: ensureRootFolder()
        val drive = driveService ?: return@withContext emptyList()

        val result = drive.files().list()
            .setQ("'$folderId' in parents and mimeType='$MIME_FOLDER' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .setOrderBy("name")
            .execute()

        result.files.map { folder ->
            val photos = drive.files().list()
                .setQ("'${folder.id}' in parents and mimeType='$MIME_IMAGE' and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime, thumbnailLink)")
                .setOrderBy("modifiedTime desc")
                .setPageSize(1)
                .execute()

            val lastPhoto = photos.files.firstOrNull()

            DeviceInfo(
                id = folder.id,
                name = folder.name,
                folderId = folder.id,
                photoCount = photos.files.size,
                lastPhotoId = lastPhoto?.id,
                lastPhotoTimestamp = lastPhoto?.modifiedTime?.value
            )
        }
    }

    /**
     * List photos in a specific device folder.
     */
    suspend fun listPhotos(deviceFolderId: String, pageSize: Int = 50): List<PhotoInfo> = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext emptyList()

        val result = drive.files().list()
            .setQ("'$deviceFolderId' in parents and mimeType='$MIME_IMAGE' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id, name, modifiedTime, thumbnailLink, webViewLink)")
            .setOrderBy("modifiedTime desc")
            .setPageSize(pageSize)
            .execute()

        result.files.map { file ->
            PhotoInfo(
                id = file.id,
                name = file.name,
                timestamp = file.modifiedTime?.value,
                thumbnailLink = file.thumbnailLink,
                webViewLink = file.webViewLink
            )
        }
    }

    /**
     * Download a photo's bytes for preview.
     */
    suspend fun downloadPhotoBytes(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext null

        try {
            drive.files().get(fileId)
                .executeMediaAsInputStream()
                .readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download photo: ${e.message}")
            null
        }
    }

    /**
     * Get total photo count across all devices.
     */
    suspend fun getTotalPhotoCount(): Int = withContext(Dispatchers.IO) {
        val devices = listDevices()
        devices.sumOf { it.photoCount }
    }

    /**
     * Poll for changes using Drive Changes API.
     */
    suspend fun pollForChanges(): Int = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext 0
        val folderId = rootFolderId ?: return@withContext 0

        try {
            if (lastChangeToken == null) {
                val tokenResponse = drive.changes().getStartPageToken().execute()
                lastChangeToken = tokenResponse.startPageToken
                Log.i(TAG, "Initial change token: $lastChangeToken")
                return@withContext 0
            }

            var changeCount = 0
            var pageToken = lastChangeToken

            while (pageToken != null) {
                val changes = drive.changes().list(pageToken)
                    .setFields("nextPageToken, newStartPageToken, changes(fileId, file(name, parents, mimeType))")
                    .execute()

                for (change in changes.changes ?: emptyList()) {
                    val file = change.file
                    if (file != null && file.parents?.contains(folderId) == true) {
                        changeCount++
                    }
                }

                if (changes.newStartPageToken != null) {
                    lastChangeToken = changes.newStartPageToken
                    pageToken = null
                } else {
                    pageToken = changes.nextPageToken
                }
            }

            if (changeCount > 0) {
                Log.i(TAG, "Detected $changeCount changes in Drive folder")
            }

            changeCount
        } catch (e: Exception) {
            Log.e(TAG, "Poll error: ${e.message}")
            0
        }
    }

    /**
     * Get the pairing data for QR code generation.
     */
    suspend fun getPairingData(): PairingQRData {
        val folderId = rootFolderId ?: ensureRootFolder()
        return PairingQRData(
            folderId = folderId,
            adminEmail = accountName ?: "",
            folderName = ROOT_FOLDER_NAME
        )
    }
}
