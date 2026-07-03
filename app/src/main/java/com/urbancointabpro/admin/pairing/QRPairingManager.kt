package com.urbancointabpro.admin.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

/**
 * Data class for the admin app's QR code (displayed on SetupScreen).
 * Contains Drive folder info so a kiosk could scan it.
 */
data class PairingQRData(
    val folderId: String,
    val adminEmail: String,
    val folderName: String,
    val type: String = "cointabpro_pair"
)

/**
 * Data class for the kiosk's QR code (displayed on kiosk Cloud tab).
 * Contains the device ID so the admin app can scan and pair it.
 */
data class KioskQRData(
    val action: String,
    val deviceId: String
)

class QRPairingManager {

    /**
     * Generate a QR bitmap for the admin app's pairing data.
     * This QR is shown on the SetupScreen for a kiosk to scan.
     */
    fun generateQRBitmap(data: PairingQRData, size: Int = 512): Bitmap? {
        return try {
            val json = JSONObject().apply {
                put("type", data.type)
                put("folderId", data.folderId)
                put("adminEmail", data.adminEmail)
                put("folderName", data.folderName)
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(json.toString(), BarcodeFormat.QR_CODE, size, size)

            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: WriterException) {
            null
        }
    }

    /**
     * Parse a QR string as admin pairing data.
     */
    fun parseQRData(qrString: String): PairingQRData? {
        return try {
            val json = JSONObject(qrString)
            if (json.optString("type") != "cointabpro_pair") return null
            PairingQRData(
                folderId = json.getString("folderId"),
                adminEmail = json.getString("adminEmail"),
                folderName = json.getString("folderName")
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse a QR string scanned from a kiosk device.
     * Expected format: {"action":"pair","deviceId":"<id>"}
     */
    fun parseKioskQR(qrString: String): KioskQRData? {
        return try {
            val json = JSONObject(qrString)
            val action = json.optString("action")
            val deviceId = json.optString("deviceId")
            if (action == "pair" && deviceId.isNotEmpty()) {
                KioskQRData(action = action, deviceId = deviceId)
            } else {
                null
            }
        } catch (e: Exception) {
            // Not JSON — might be a plain device ID
            if (qrString.isNotBlank() && qrString.length in 4..64) {
                KioskQRData(action = "pair", deviceId = qrString.trim())
            } else {
                null
            }
        }
    }
}
