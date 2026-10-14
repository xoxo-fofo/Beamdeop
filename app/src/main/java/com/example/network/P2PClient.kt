package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.TransferRepository
import com.example.model.SelectedFile
import com.example.model.TransferDirection
import com.example.model.TransferProgress
import com.example.model.TransferStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

class P2PClient(
    private val context: Context,
    private val repository: TransferRepository,
    private val scope: CoroutineScope
) {
    private var clientSocket: Socket? = null
    private var clientJob: Job? = null
    private var isCancelled = false

    private val _sendProgress = MutableStateFlow(TransferProgress())
    val sendProgress: StateFlow<TransferProgress> = _sendProgress.asStateFlow()

    fun sendFiles(
        targetHost: String,
        targetPort: Int,
        peerDeviceName: String,
        files: List<SelectedFile>
    ) {
        if (files.isEmpty()) return
        cancelActiveTransfer()
        isCancelled = false

        clientJob = scope.launch(Dispatchers.IO) {
            _sendProgress.value = TransferProgress(
                status = TransferStatus.CONNECTING,
                direction = TransferDirection.SENT,
                peerName = peerDeviceName,
                totalFiles = files.size,
                totalBytes = files.sumOf { it.size }
            )

            var socket: Socket? = null
            var dis: DataInputStream? = null
            var dos: DataOutputStream? = null

            try {
                Log.d(TAG, "Connecting to $targetHost:$targetPort ($peerDeviceName)...")
                socket = Socket()
                socket.tcpNoDelay = true
                socket.sendBufferSize = 128 * 1024
                socket.connect(InetSocketAddress(targetHost, targetPort), 7000) // 7s timeout
                clientSocket = socket

                val bis = BufferedInputStream(socket.getInputStream(), 64 * 1024)
                val bos = BufferedOutputStream(socket.getOutputStream(), 128 * 1024)
                dis = DataInputStream(bis)
                dos = DataOutputStream(bos)

                val localDeviceName = NetworkUtils.getDeviceName()
                val totalBytes = files.sumOf { it.size }

                // Step 1: Send Handshake
                BeamProtocol.writeHandshake(dos, localDeviceName, files.size, totalBytes)
                Log.d(TAG, "Handshake sent. Waiting for receiver ack...")

                val handshakeAck = dis.readInt()
                if (handshakeAck != BeamProtocol.MSG_HANDSHAKE_ACK) {
                    throw IOException("Receiver rejected handshake: $handshakeAck")
                }

                _sendProgress.value = _sendProgress.value.copy(
                    status = TransferStatus.TRANSFERRING,
                    bytesTransferred = 0L
                )

                var totalBytesSent = 0L
                val startTime = System.currentTimeMillis()
                var lastSpeedCalcTime = startTime
                var lastBytesAtCalc = 0L
                var currentSpeed = 0L

                val buffer = ByteArray(BeamProtocol.CHUNK_SIZE)

                // Step 2: Stream each file
                for ((index, file) in files.withIndex()) {
                    if (isCancelled || !isActive) break

                    _sendProgress.value = _sendProgress.value.copy(
                        currentFileName = file.name,
                        currentFileIndex = index + 1
                    )

                    val meta = BeamProtocol.FileMeta(
                        name = file.name,
                        size = file.size,
                        mimeType = file.mimeType
                    )
                    BeamProtocol.writeFileStart(dos, index, meta)

                    var inputStream: InputStream? = null
                    var fileSentBytes = 0L

                    try {
                        inputStream = context.contentResolver.openInputStream(file.uri)
                            ?: throw IOException("Cannot open input stream for ${file.uri}")

                        val bufferedInput = BufferedInputStream(inputStream, 128 * 1024)

                        while (fileSentBytes < file.size && !isCancelled && isActive) {
                            val toRead = Math.min(buffer.size.toLong(), file.size - fileSentBytes).toInt()
                            val bytesRead = bufferedInput.read(buffer, 0, toRead)
                            if (bytesRead == -1) break

                            dos.write(buffer, 0, bytesRead)
                            fileSentBytes += bytesRead
                            totalBytesSent += bytesRead

                            val now = System.currentTimeMillis()
                            val timeDelta = now - lastSpeedCalcTime
                            if (timeDelta >= 300) {
                                val bytesDelta = totalBytesSent - lastBytesAtCalc
                                currentSpeed = (bytesDelta * 1000L) / timeDelta
                                lastSpeedCalcTime = now
                                lastBytesAtCalc = totalBytesSent

                                val remainingBytes = totalBytes - totalBytesSent
                                val eta = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

                                _sendProgress.value = _sendProgress.value.copy(
                                    bytesTransferred = totalBytesSent,
                                    speedBytesPerSec = currentSpeed,
                                    etaSeconds = eta
                                )
                            }
                        }
                        dos.flush()
                    } finally {
                        try {
                            inputStream?.close()
                        } catch (e: Exception) {
                            // ignore
                        }
                    }

                    // Wait for File ACK
                    val fileEndAck = dis.readInt()
                    if (fileEndAck != BeamProtocol.MSG_FILE_END) {
                        throw IOException("Did not receive MSG_FILE_END ack for ${file.name}")
                    }

                    // Save record in Room database
                    repository.recordTransfer(
                        fileName = file.name,
                        fileSize = file.size,
                        mimeType = file.mimeType,
                        direction = TransferDirection.SENT.name,
                        peerName = peerDeviceName,
                        peerAddress = targetHost,
                        localFilePath = file.uri.toString(),
                        isSuccess = fileSentBytes == file.size
                    )
                }

                // Step 3: Complete transfer
                dos.writeInt(BeamProtocol.MSG_ALL_COMPLETE)
                dos.flush()

                val finalAck = dis.readInt()
                if (finalAck == BeamProtocol.MSG_ALL_COMPLETE) {
                    _sendProgress.value = _sendProgress.value.copy(
                        status = TransferStatus.COMPLETED,
                        bytesTransferred = totalBytes,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                }

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Transfer failed: ${e.message}", e)
                    _sendProgress.value = _sendProgress.value.copy(
                        status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.FAILED,
                        errorMessage = e.localizedMessage ?: "Connection failed"
                    )
                }
            } finally {
                try {
                    dos?.close()
                    dis?.close()
                    socket?.close()
                } catch (e: Exception) {
                    // ignore
                }
                clientSocket = null
            }
        }
    }

    fun cancelActiveTransfer() {
        isCancelled = true
        try {
            clientSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        clientJob?.cancel()
        _sendProgress.value = _sendProgress.value.copy(
            status = TransferStatus.CANCELLED,
            speedBytesPerSec = 0L
        )
    }

    fun resetProgress() {
        _sendProgress.value = TransferProgress()
    }

    companion object {
        private const val TAG = "BeamDrop_Client"
    }
}
