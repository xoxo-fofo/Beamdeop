package com.example.network

import java.io.DataInputStream
import java.io.DataOutputStream

object BeamProtocol {
    const val MAGIC_HEADER = 0x4245414D // "BEAM"
    const val PROTOCOL_VERSION = 1

    const val MSG_HANDSHAKE = 10
    const val MSG_HANDSHAKE_ACK = 11
    const val MSG_FILE_START = 20
    const val MSG_FILE_CHUNK = 21
    const val MSG_FILE_END = 22
    const val MSG_ALL_COMPLETE = 30
    const val MSG_CANCEL = 99

    const val CHUNK_SIZE = 64 * 1024 // 64 KB high performance buffer

    data class FileMeta(
        val name: String,
        val size: Long,
        val mimeType: String
    )

    fun writeHandshake(dos: DataOutputStream, senderDeviceName: String, fileCount: Int, totalBytes: Long) {
        dos.writeInt(MAGIC_HEADER)
        dos.writeInt(PROTOCOL_VERSION)
        dos.writeInt(MSG_HANDSHAKE)
        dos.writeUTF(senderDeviceName)
        dos.writeInt(fileCount)
        dos.writeLong(totalBytes)
        dos.flush()
    }

    fun readHandshake(dis: DataInputStream): HandshakeData {
        val magic = dis.readInt()
        if (magic != MAGIC_HEADER) {
            throw IllegalStateException("Invalid protocol magic header: $magic")
        }
        val version = dis.readInt()
        if (version != PROTOCOL_VERSION) {
            throw IllegalStateException("Unsupported protocol version: $version")
        }
        val msgType = dis.readInt()
        if (msgType != MSG_HANDSHAKE) {
            throw IllegalStateException("Expected handshake message, received: $msgType")
        }
        val senderDeviceName = dis.readUTF()
        val fileCount = dis.readInt()
        val totalBytes = dis.readLong()
        return HandshakeData(senderDeviceName, fileCount, totalBytes)
    }

    data class HandshakeData(
        val senderName: String,
        val fileCount: Int,
        val totalBytes: Long
    )

    fun writeFileStart(dos: DataOutputStream, fileIndex: Int, meta: FileMeta) {
        dos.writeInt(MSG_FILE_START)
        dos.writeInt(fileIndex)
        dos.writeUTF(meta.name)
        dos.writeLong(meta.size)
        dos.writeUTF(meta.mimeType)
        dos.flush()
    }

    fun readFileStart(dis: DataInputStream): FileMeta {
        val msgType = dis.readInt()
        if (msgType != MSG_FILE_START) {
            throw IllegalStateException("Expected MSG_FILE_START, got $msgType")
        }
        val fileIndex = dis.readInt()
        val fileName = dis.readUTF()
        val fileSize = dis.readLong()
        val mimeType = dis.readUTF()
        return FileMeta(fileName, fileSize, mimeType)
    }
}
