package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.TransferRepository
import com.example.model.TransferDirection
import com.example.model.TransferProgress
import com.example.model.TransferStatus
import com.example.util.StorageHelper
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
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class P2PServer(
    private val context: Context,
    private val repository: TransferRepository,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var currentClientSocket: Socket? = null
    private var serverJob: Job? = null
    private var isCancelled = false

    private val _serverRunning = MutableStateFlow(false)
    val serverRunning: StateFlow<Boolean> = _serverRunning.asStateFlow()

    private val _receiveProgress = MutableStateFlow(TransferProgress())
    val receiveProgress: StateFlow<TransferProgress> = _receiveProgress.asStateFlow()

    fun start(port: Int = NetworkUtils.DEFAULT_PORT) {
        if (_serverRunning.value) return
        isCancelled = false

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                _serverRunning.value = true
                Log.d(TAG, "P2P Server listening on port $port")

                while (isActive && !isCancelled) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        currentClientSocket = client
                        handleClientConnection(client)
                    } catch (e: SocketException) {
                        if (isCancelled || !isActive) break
                        Log.e(TAG, "Server socket exception: ${e.message}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error accepting client: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server startup error: ${e.message}")
            } finally {
                _serverRunning.value = false
            }
        }
    }

    private suspend fun handleClientConnection(socket: Socket) = withContext(Dispatchers.IO) {
        val remoteIp = socket.inetAddress.hostAddress ?: "Unknown"
        var dataInputStream: DataInputStream? = null
        var dataOutputStream: DataOutputStream? = null

        try {
            socket.tcpNoDelay = true
            socket.receiveBufferSize = 128 * 1024

            val bis = BufferedInputStream(socket.getInputStream(), 128 * 1024)
            val bos = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            dataInputStream = DataInputStream(bis)
            dataOutputStream = DataOutputStream(bos)

            // Step 1: Read Handshake
            val handshake = BeamProtocol.readHandshake(dataInputStream)
            Log.d(TAG, "Incoming transfer from ${handshake.senderName}: ${handshake.fileCount} files, ${handshake.totalBytes} bytes")

            // Send Handshake ACK
            dataOutputStream.writeInt(BeamProtocol.MSG_HANDSHAKE_ACK)
            dataOutputStream.flush()

            _receiveProgress.value = TransferProgress(
                status = TransferStatus.TRANSFERRING,
                direction = TransferDirection.RECEIVED,
                peerName = handshake.senderName,
                totalFiles = handshake.fileCount,
                totalBytes = handshake.totalBytes,
                bytesTransferred = 0L
            )

            var totalBytesTransferred = 0L
            val startTime = System.currentTimeMillis()
            var lastSpeedCalcTime = startTime
            var lastBytesAtCalc = 0L
            var currentSpeed = 0L

            val buffer = ByteArray(BeamProtocol.CHUNK_SIZE)

            // Step 2: Receive each file
            for (fileIndex in 0 until handshake.fileCount) {
                if (isCancelled || !isActive) break

                val fileMeta = BeamProtocol.readFileStart(dataInputStream)
                val targetFile = StorageHelper.getUniqueTargetFile(context, fileMeta.name)
                Log.d(TAG, "Receiving file $fileIndex: ${fileMeta.name} (${fileMeta.size} bytes) -> ${targetFile.absolutePath}")

                _receiveProgress.value = _receiveProgress.value.copy(
                    currentFileName = fileMeta.name,
                    currentFileIndex = fileIndex + 1
                )

                var fileBytesRead = 0L
                val fos = FileOutputStream(targetFile)
                val fileBos = BufferedOutputStream(fos, 128 * 1024)

                try {
                    while (fileBytesRead < fileMeta.size && !isCancelled && isActive) {
                        val toRead = Math.min(buffer.size.toLong(), fileMeta.size - fileBytesRead).toInt()
                        val bytesRead = dataInputStream.read(buffer, 0, toRead)
                        if (bytesRead == -1) {
                            throw IOException("Unexpected End of Stream while reading file ${fileMeta.name}")
                        }
                        fileBos.write(buffer, 0, bytesRead)
                        fileBytesRead += bytesRead
                        totalBytesTransferred += bytesRead

                        // Periodic speed calculation every 300ms
                        val now = System.currentTimeMillis()
                        val timeDelta = now - lastSpeedCalcTime
                        if (timeDelta >= 300) {
                            val bytesDelta = totalBytesTransferred - lastBytesAtCalc
                            currentSpeed = (bytesDelta * 1000L) / timeDelta
                            lastSpeedCalcTime = now
                            lastBytesAtCalc = totalBytesTransferred

                            val remainingBytes = handshake.totalBytes - totalBytesTransferred
                            val eta = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

                            _receiveProgress.value = _receiveProgress.value.copy(
                                bytesTransferred = totalBytesTransferred,
                                speedBytesPerSec = currentSpeed,
                                etaSeconds = eta
                            )
                        }
                    }
                    fileBos.flush()
                } finally {
                    try {
                        fileBos.close()
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                // Send File ACK
                dataOutputStream.writeInt(BeamProtocol.MSG_FILE_END)
                dataOutputStream.flush()

                // Save record in Room database
                repository.recordTransfer(
                    fileName = fileMeta.name,
                    fileSize = fileMeta.size,
                    mimeType = fileMeta.mimeType,
                    direction = TransferDirection.RECEIVED.name,
                    peerName = handshake.senderName,
                    peerAddress = remoteIp,
                    localFilePath = targetFile.absolutePath,
                    isSuccess = fileBytesRead == fileMeta.size
                )
            }

            // Step 3: Complete transfer
            val finalMsg = dataInputStream.readInt()
            if (finalMsg == BeamProtocol.MSG_ALL_COMPLETE) {
                dataOutputStream.writeInt(BeamProtocol.MSG_ALL_COMPLETE)
                dataOutputStream.flush()

                _receiveProgress.value = _receiveProgress.value.copy(
                    status = TransferStatus.COMPLETED,
                    bytesTransferred = handshake.totalBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L
                )
            }

        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e(TAG, "Error receiving files: ${e.message}", e)
                _receiveProgress.value = _receiveProgress.value.copy(
                    status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.FAILED,
                    errorMessage = e.localizedMessage ?: "Connection error"
                )
            }
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
            currentClientSocket = null
        }
    }

    fun cancelActiveTransfer() {
        isCancelled = true
        try {
            currentClientSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        _receiveProgress.value = _receiveProgress.value.copy(
            status = TransferStatus.CANCELLED,
            speedBytesPerSec = 0L
        )
    }

    fun resetProgress() {
        _receiveProgress.value = TransferProgress()
    }

    fun stop() {
        isCancelled = true
        try {
            currentClientSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverJob?.cancel()
        _serverRunning.value = false
    }

    companion object {
        private const val TAG = "BeamDrop_Server"
    }
}
