package com.urbancointabpro.admin.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

data class PairingQRData(
    val folderId: String,
    val adminEmail: String,
    val folderName: String,
    val type: String = "cointabpro_pair"
)

class QRPairingManager {

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
}
