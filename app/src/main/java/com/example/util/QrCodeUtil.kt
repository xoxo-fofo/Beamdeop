package com.example.util

import android.graphics.Bitmap
import android.net.Uri
import com.example.model.QrPayload
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrCodeUtil {

    private const val SCHEME = "beamdrop"
    private const val HOST = "p2p"

    fun createPayloadString(ip: String, port: Int, deviceName: String, ssid: String? = null): String {
        val uriBuilder = Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter("ip", ip)
            .appendQueryParameter("port", port.toString())
            .appendQueryParameter("name", deviceName)

        if (!ssid.isNullOrBlank()) {
            uriBuilder.appendQueryParameter("ssid", ssid)
        }
        return uriBuilder.build().toString()
    }

    fun parsePayload(qrString: String): QrPayload? {
        return try {
            val uri = Uri.parse(qrString.trim())
            if (uri.scheme == SCHEME || qrString.startsWith("$SCHEME://$HOST")) {
                val ip = uri.getQueryParameter("ip") ?: return null
                val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8988
                val name = uri.getQueryParameter("name") ?: "Peer Device"
                val ssid = uri.getQueryParameter("ssid")
                QrPayload(ip = ip, port = port, deviceName = name, ssid = ssid)
            } else if (qrString.contains(":") && qrString.split(":").size == 2) {
                // Raw IP:PORT format fallback (e.g. 192.168.43.1:8988)
                val parts = qrString.split(":")
                val ip = parts[0].trim()
                val port = parts[1].trim().toIntOrNull() ?: 8988
                QrPayload(ip = ip, port = port, deviceName = "BeamDrop Peer")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun generateQrBitmap(
        content: String,
        width: Int = 512,
        height: Int = 512,
        darkColor: Int = 0xFF00E5FF.toInt(),
        lightColor: Int = 0xFF0D1B2A.toInt()
    ): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 2)
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            }
            val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints)
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else lightColor
                }
            }

            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
