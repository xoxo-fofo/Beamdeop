package com.example.data

import kotlinx.coroutines.flow.Flow

class TransferRepository(private val transferDao: TransferDao) {
    val allTransfers: Flow<List<TransferEntity>> = transferDao.getAllTransfersFlow()

    suspend fun recordTransfer(
        fileName: String,
        fileSize: Long,
        mimeType: String,
        direction: String,
        peerName: String,
        peerAddress: String,
        localFilePath: String?,
        isSuccess: Boolean
    ): Long {
        val entity = TransferEntity(
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType,
            direction = direction,
            peerName = peerName,
            peerAddress = peerAddress,
            localFilePath = localFilePath,
            isSuccess = isSuccess
        )
        return transferDao.insertTransfer(entity)
    }

    suspend fun delete(id: Long) {
        transferDao.deleteById(id)
    }

    suspend fun clearAll() {
        transferDao.clearAll()
    }
}
