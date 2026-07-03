package com.urbancointabpro.admin.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Helper class for writing pairing data to Firestore.
 *
 * Firestore is used ONLY for storing device pairing metadata
 * (driveFolderId) in the license document. This is NOT traffic —
 * it's configuration data that the kiosk reads on startup.
 *
 * Firestore architecture:
 * - Collection: "licenses"
 * - Document: queried by deviceId (stored in the doc)
 * - Fields added: driveFolderId, pairedAt, adminEmail
 */
class FirestoreHelper {
    companion object {
        private const val TAG = "FirestoreHelper"
        private const val COLLECTION_LICENSES = "licenses"
    }

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /**
     * Write pairing data to Firestore so the kiosk can discover its Drive folder.
     * Uses merge to avoid overwriting existing license data.
     */
    suspend fun writePairingData(
        deviceId: String,
        driveFolderId: String,
        adminEmail: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Find the license document for this device
            val query = db.collection(COLLECTION_LICENSES)
                .whereEqualTo("deviceId", deviceId)
                .limit(1)
                .get()
                .await()

            val pairingData = mapOf(
                "driveFolderId" to driveFolderId,
                "pairedAt" to System.currentTimeMillis(),
                "adminEmail" to adminEmail
            )

            if (query.documents.isNotEmpty()) {
                // Update existing license document
                val docId = query.documents[0].id
                db.collection(COLLECTION_LICENSES)
                    .document(docId)
                    .set(pairingData, SetOptions.merge())
                    .await()
                Log.i(TAG, "Pairing data written to existing doc: $docId")
            } else {
                // Create a new document for this device
                val newData = mapOf(
                    "deviceId" to deviceId,
                    "driveFolderId" to driveFolderId,
                    "pairedAt" to System.currentTimeMillis(),
                    "adminEmail" to adminEmail,
                    "status" to "paired"
                )
                db.collection(COLLECTION_LICENSES)
                    .add(newData)
                    .await()
                Log.i(TAG, "New pairing document created for device: $deviceId")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write pairing data: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Read the Drive folder ID for a device from Firestore.
     */
    suspend fun getDriveFolderId(deviceId: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = db.collection(COLLECTION_LICENSES)
                .whereEqualTo("deviceId", deviceId)
                .limit(1)
                .get()
                .await()

            query.documents.firstOrNull()?.getString("driveFolderId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read pairing data: ${e.message}")
            null
        }
    }
}
