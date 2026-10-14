package com.example.model

import android.net.Uri

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String
)

data class PeerDevice(
    val id: String,
    val name: String,
    val hostAddress: String,
    val port: Int,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

enum class TransferDirection {
    SENT,
    RECEIVED
}

enum class TransferStatus {
    IDLE,
    CONNECTING,
    WAITING_ACCEPT,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferProgress(
    val status: TransferStatus = TransferStatus.IDLE,
    val direction: TransferDirection = TransferDirection.SENT,
    val peerName: String = "",
    val currentFileName: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}

data class QrPayload(
    val ip: String,
    val port: Int,
    val deviceName: String,
    val ssid: String? = null
)
