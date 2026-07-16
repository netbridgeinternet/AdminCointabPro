package com.urbancointabpro.admin.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.urbancointabpro.admin.pairing.PairingQRData
import java.io.IOException
import java.net.SocketTimeoutException
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

/**
 * Special exception thrown when Google needs user consent for Drive access.
 * The calling code MUST launch the intent from this exception to show the consent screen.
 */
class DriveConsentRequiredException(val consentIntent: Intent) : Exception("Google Drive consent required")

class DriveManager(private val context: Context) {
    companion object {
        private const val TAG = "DriveManager"
        const val ROOT_FOLDER_NAME = "CointabPro Photos"
        const val MIME_FOLDER = "application/vnd.google-apps.folder"
        const val MIME_IMAGE = "image/jpeg"
        const val PREFS_NAME = "admin_cointabpro_prefs"
        const val KEY_ACCOUNT_NAME = "account_name"
        const val REQUEST_CODE_CONSENT = 9002
        // The scope string for GoogleAuthUtil.getToken()
        private val DRIVE_SCOPE = DriveScopes.DRIVE

        // Service account email for Kiosk app — the service account is created
        // in the newurbancointabpro Google Cloud project. When a device folder
        // is created, it is shared with this email so the Kiosk can upload
        // screenshots via the service account (no user sign-in needed on Kiosk).
        const val SERVICE_ACCOUNT_EMAIL = "kiosk-drive-uploader@newurbancointabpro.iam.gserviceaccount.com"
    }

    private var driveService: Drive? = null
    private var rootFolderId: String? = null
    private var lastChangeToken: String? = null
    private var credential: GoogleAccountCredential? = null
    private var accountName: String? = null

    /**
     * Get or create the GoogleAccountCredential for Drive API access.
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
     */
    fun getAccountPickerIntent(): Intent {
        return getCredential().newChooseAccountIntent()
    }

    /**
     * Proactively request OAuth consent for Drive access.
     * This MUST be called BEFORE any Drive API calls to ensure the user
     * has granted permission. It uses GoogleAuthUtil.getToken() which
     * properly throws UserRecoverableAuthException when consent is needed.
     *
     * Returns null if consent is already granted (token obtained successfully).
     * Returns the consent Intent if user needs to grant consent.
     */
    suspend fun requestConsentIfNeeded(account: String): Intent? = withContext(Dispatchers.IO) {
        try {
            val acct = Account(account, "com.google")
            // This call triggers the OAuth consent flow if not yet granted.
            // It MUST be called on a background thread.
            val token = GoogleAuthUtil.getToken(context, acct, "oauth2:$DRIVE_SCOPE")
            Log.i(TAG, "Drive consent already granted, token obtained (${token.length} chars)")
            // Token obtained — consent is already granted
            null
        } catch (e: UserRecoverableAuthException) {
            // Consent is needed — return the intent to show the consent screen
            Log.i(TAG, "Drive consent required, returning intent")
            e.intent
        } catch (e: GoogleAuthException) {
            Log.e(TAG, "Google auth error during consent check: ${e.javaClass.simpleName}: ${e.message}")
            // Fatal auth error — can't proceed
            throw SecurityException("Google authentication failed: ${e.message ?: "The app may need to be registered in Google Cloud Console."}")
        } catch (e: IOException) {
            Log.e(TAG, "Network error during consent check: ${e.message}")
            throw IOException("Network error during sign-in. Please check your internet connection and try again.")
        }
    }

    /**
     * Initialize Drive service after account is selected AND consent is granted.
     */
    fun initializeDrive(selectedAccountName: String) {
        accountName = selectedAccountName
        getCredential().selectedAccountName = selectedAccountName

        val cred = getCredential()

        // Chain BOTH the credential (for OAuth auth headers) AND the timeout initializer.
        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            HttpRequestInitializer { request ->
                cred.initialize(request)       // Set OAuth authentication headers
                request.connectTimeout = 30_000 // 30 seconds to connect
                request.readTimeout = 60_000    // 60 seconds to read
            }
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
     * Execute a Drive API call, catching all authentication-related exceptions
     * and wrapping them appropriately so the UI can handle consent or show
     * meaningful error messages.
     */
    private fun <T> driveExecute(block: () -> T): T {
        return try {
            block()
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "OAuth consent required (IO): ${e.message}")
            throw DriveConsentRequiredException(e.intent)
        } catch (e: GoogleJsonResponseException) {
            Log.e(TAG, "Drive API error ${e.statusCode}: ${e.message}")
            throw IOException("Drive API error ${e.statusCode}: ${e.details?.errors?.firstOrNull()?.message ?: e.message}")
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Drive API timeout: ${e.message}")
            throw IOException("Connection timed out. Please check your internet connection and try again.")
        } catch (e: IOException) {
            Log.e(TAG, "Drive IO error: ${e.javaClass.name}: ${e.message}", e)
            // Walk the cause chain looking for UserRecoverableAuthIOException
            var cause: Throwable? = e
            while (cause != null) {
                if (cause is UserRecoverableAuthIOException) {
                    Log.w(TAG, "Found UserRecoverableAuthIOException in cause chain")
                    throw DriveConsentRequiredException(cause.intent)
                }
                cause = cause.cause
            }
            // Build a detailed error message including the full cause chain
            val causeChain = buildString {
                var c: Throwable? = e.cause
                while (c != null) {
                    append(" → ${c.javaClass.simpleName}")
                    if (c.message != null) append(": ${c.message}")
                    c = c.cause
                }
            }
            val detail = e.message ?: "${e.javaClass.simpleName}: (no message)"
            throw IOException("$detail$causeChain")
        }
    }

    /**
     * Create the root "CointabPro Photos" folder if it doesn't exist.
     * Returns the folder ID.
     */
    suspend fun ensureRootFolder(): String = withContext(Dispatchers.IO) {
        val drive = driveService ?: throw IllegalStateException("Drive not initialized")

        // Check if folder already exists
        val existing = driveExecute {
            drive.files().list()
                .setQ("name='${ROOT_FOLDER_NAME}' and mimeType='${MIME_FOLDER}' and trashed=false and 'me' in owners")
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
        }

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
        val folder = driveExecute {
            drive.files().create(metadata)
                .setFields("id, name, webViewLink")
                .execute()
        }

        rootFolderId = folder.id
        Log.i(TAG, "Created root folder: $rootFolderId")

        // Share folder: allow anyone with link to upload
        try {
            shareFolderForUpload(rootFolderId!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to share folder: ${e.message}")
        }

        rootFolderId!!
    }

    /**
     * Share the folder so kiosks can upload photos.
     */
    private suspend fun shareFolderForUpload(folderId: String) = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext

        try {
            val perms = driveExecute {
                drive.permissions().list(folderId)
                    .setFields("permissions(id, type, role)")
                    .execute()
            }

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
            driveExecute {
                drive.permissions().create(folderId, permission)
                    .setFields("id")
                    .execute()
            }
            Log.i(TAG, "Folder shared: anyone with link can upload")
        } catch (e: DriveConsentRequiredException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share folder: ${e.message}")
        }
    }

    /**
     * Share folder with a specific email (for Service Account pairing).
     * @param sendNotification Whether to send a notification email (false for service accounts)
     */
    suspend fun shareFolderWithEmail(folderId: String, email: String, sendNotification: Boolean = true) = withContext(Dispatchers.IO) {
        val drive = driveService ?: throw IllegalStateException("Drive not initialized")

        // Check if already shared with this email
        try {
            val perms = driveExecute {
                drive.permissions().list(folderId)
                    .setFields("permissions(id, type, role, emailAddress)")
                    .execute()
            }
            val alreadyShared = perms.permissions.any {
                it.type == "user" && it.emailAddress == email
            }
            if (alreadyShared) {
                Log.i(TAG, "Folder already shared with: $email")
                return@withContext
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check existing permissions: ${e.message}")
        }

        val permission = Permission().apply {
            type = "user"
            role = "writer"
            emailAddress = email
        }
        driveExecute {
            drive.permissions().create(folderId, permission)
                .setSendNotificationEmail(sendNotification)
                .apply {
                    if (sendNotification) {
                        setEmailMessage("CointabPro Admin has shared a photo storage folder with you.")
                    }
                }
                .setFields("id")
                .execute()
        }
        Log.i(TAG, "Folder shared with: $email (notification=$sendNotification)")
    }

    /**
     * Share a device folder with the Kiosk service account.
     * This allows the Kiosk app to upload screenshots without user sign-in.
     */
    suspend fun shareWithServiceAccount(folderId: String) {
        try {
            shareFolderWithEmail(folderId, SERVICE_ACCOUNT_EMAIL, sendNotification = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share folder with service account: ${e.message}")
            // Don't throw — the folder still works with user account auth
        }
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

        val existing = driveExecute {
            drive.files().list()
                .setQ("name='$folderName' and '$rootId' in parents and mimeType='$MIME_FOLDER' and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
        }

        if (existing.files.isNotEmpty()) {
            val existingId = existing.files[0].id
            Log.i(TAG, "Device folder already exists: $existingId")

            // Ensure service account has access (for folders created before SA support)
            try {
                shareWithServiceAccount(existingId)
            } catch (e: Exception) {
                Log.w(TAG, "Could not share existing folder with SA: ${e.message}")
            }

            return@withContext existingId
        }

        val metadata = File().apply {
            name = folderName
            mimeType = MIME_FOLDER
            parents = listOf(rootId)
        }
        val folder = driveExecute {
            drive.files().create(metadata)
                .setFields("id, name, webViewLink")
                .execute()
        }

        Log.i(TAG, "Created device folder: ${folder.id} ($folderName)")

        // Share the new folder with the service account so the Kiosk
        // can upload screenshots without user sign-in
        try {
            shareWithServiceAccount(folder.id)
        } catch (e: Exception) {
            Log.w(TAG, "Could not share new folder with SA: ${e.message}")
        }

        folder.id
    }

    /**
     * Get the web URL to open a Drive folder in browser.
     */
    suspend fun getFolderWebUrl(folderId: String): String? = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext null

        try {
            val file = driveExecute {
                drive.files().get(folderId)
                    .setFields("webViewLink")
                    .execute()
            }
            file.webViewLink
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get folder URL: ${e.message}")
            "https://drive.google.com/drive/folders/$folderId"
        }
    }

    /**
     * Get an Intent to open a Drive folder in the Google Drive app or browser.
     */
    fun getOpenFolderIntent(folderId: String): Intent {
        val driveIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://drive.google.com/drive/folders/$folderId")
            setPackage("com.google.android.apps.docs")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val resolveInfo = context.packageManager.resolveActivity(driveIntent, 0)
        return if (resolveInfo != null) {
            driveIntent
        } else {
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

        val result = driveExecute {
            drive.files().list()
                .setQ("'$folderId' in parents and mimeType='$MIME_FOLDER' and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setOrderBy("name")
                .execute()
        }

        result.files.map { folder ->
            val photos = driveExecute {
                drive.files().list()
                    .setQ("'${folder.id}' in parents and mimeType='$MIME_IMAGE' and trashed=false")
                    .setSpaces("drive")
                    .setFields("files(id, name, modifiedTime, thumbnailLink)")
                    .setOrderBy("modifiedTime desc")
                    .setPageSize(1)
                    .execute()
            }

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

        val result = driveExecute {
            drive.files().list()
                .setQ("'$deviceFolderId' in parents and mimeType='$MIME_IMAGE' and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime, thumbnailLink, webViewLink)")
                .setOrderBy("modifiedTime desc")
                .setPageSize(pageSize)
                .execute()
        }

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
            driveExecute {
                drive.files().get(fileId)
                    .executeMediaAsInputStream()
                    .readBytes()
            }
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
                val tokenResponse = driveExecute {
                    drive.changes().getStartPageToken().execute()
                }
                lastChangeToken = tokenResponse.startPageToken
                Log.i(TAG, "Initial change token: $lastChangeToken")
                return@withContext 0
            }

            var changeCount = 0
            var pageToken = lastChangeToken

            while (pageToken != null) {
                val changes = driveExecute {
                    drive.changes().list(pageToken)
                        .setFields("nextPageToken, newStartPageToken, changes(fileId, file(name, parents, mimeType))")
                        .execute()
                }

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
        } catch (e: DriveConsentRequiredException) {
            throw e
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
