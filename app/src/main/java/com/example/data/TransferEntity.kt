package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val direction: String, // "SENT" or "RECEIVED"
    val peerName: String,
    val peerAddress: String,
    val timestamp: Long = System.currentTimeMillis(),
    val localFilePath: String? = null,
    val isSuccess: Boolean = true
)
