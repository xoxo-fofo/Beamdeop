package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TransferEntity
import com.example.data.TransferRepository
import com.example.model.PeerDevice
import com.example.model.QrPayload
import com.example.model.SelectedFile
import com.example.model.TransferProgress
import com.example.model.TransferStatus
import com.example.network.NetworkUtils
import com.example.network.NsdHelper
import com.example.network.P2PClient
import com.example.network.P2PServer
import com.example.util.FileUtils
import com.example.util.QrCodeUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransferRepository
    private val p2pServer: P2PServer
    private val p2pClient: P2PClient
    private var nsdHelper: NsdHelper? = null

    val historyTransfers: StateFlow<List<TransferEntity>>
    val serverRunning: StateFlow<Boolean>
    val receiveProgress: StateFlow<TransferProgress>
    val sendProgress: StateFlow<TransferProgress>

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _localIpAddress = MutableStateFlow<String?>(null)
    val localIpAddress: StateFlow<String?> = _localIpAddress.asStateFlow()

    private val _wifiSsid = MutableStateFlow<String?>(null)
    val wifiSsid: StateFlow<String?> = _wifiSsid.asStateFlow()

    private val _isDiscoveringPeers = MutableStateFlow(false)
    val isDiscoveringPeers: StateFlow<Boolean> = _isDiscoveringPeers.asStateFlow()

    private val _qrPayloadString = MutableStateFlow("")
    val qrPayloadString: StateFlow<String> = _qrPayloadString.asStateFlow()

    init {
        val database = AppDatabase.getInstance(application)
        repository = TransferRepository(database.transferDao())
        p2pServer = P2PServer(application, repository, viewModelScope)
        p2pClient = P2PClient(application, repository, viewModelScope)

        historyTransfers = repository.allTransfers.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        serverRunning = p2pServer.serverRunning
        receiveProgress = p2pServer.receiveProgress
        sendProgress = p2pClient.sendProgress

        refreshNetworkInfo()
        setupNsd()
    }

    fun refreshNetworkInfo() {
        val ip = NetworkUtils.getLocalIpAddress()
        _localIpAddress.value = ip
        val ssid = NetworkUtils.getConnectedWifiSsid(getApplication())
        _wifiSsid.value = ssid

        val deviceName = NetworkUtils.getDeviceName()
        if (ip != null) {
            _qrPayloadString.value = QrCodeUtil.createPayloadString(
                ip = ip,
                port = NetworkUtils.DEFAULT_PORT,
                deviceName = deviceName,
                ssid = ssid
            )
        } else {
            _qrPayloadString.value = ""
        }
    }

    private fun setupNsd() {
        nsdHelper = NsdHelper(
            context = getApplication(),
            onPeerDiscovered = { peer ->
                val current = _discoveredPeers.value.toMutableList()
                val index = current.indexOfFirst { it.hostAddress == peer.hostAddress }
                if (index != -1) {
                    current[index] = peer
                } else {
                    current.add(peer)
                }
                _discoveredPeers.value = current
            },
            onPeerLost = { peerId ->
                val current = _discoveredPeers.value.toMutableList()
                current.removeAll { it.id == peerId }
                _discoveredPeers.value = current
            }
        )
    }

    fun startReceiveMode() {
        refreshNetworkInfo()
        p2pServer.start(NetworkUtils.DEFAULT_PORT)
        nsdHelper?.registerService(NetworkUtils.DEFAULT_PORT, NetworkUtils.getDeviceName())
    }

    fun stopReceiveMode() {
        p2pServer.stop()
        nsdHelper?.unregisterService()
    }

    fun toggleReceiveMode() {
        if (serverRunning.value) {
            stopReceiveMode()
        } else {
            startReceiveMode()
        }
    }

    fun startPeerDiscovery() {
        refreshNetworkInfo()
        _isDiscoveringPeers.value = true
        _discoveredPeers.value = emptyList()
        nsdHelper?.startDiscovery()
    }

    fun stopPeerDiscovery() {
        _isDiscoveringPeers.value = false
        nsdHelper?.stopDiscovery()
    }

    fun addSelectedUris(uris: List<Uri>) {
        val context = getApplication<Application>()
        val current = _selectedFiles.value.toMutableList()
        for (uri in uris) {
            // Check if already selected
            if (current.none { it.uri == uri }) {
                val name = FileUtils.getFileName(context, uri)
                val size = FileUtils.getFileSize(context, uri)
                val mimeType = FileUtils.getMimeType(context, uri)
                current.add(SelectedFile(uri, name, size, mimeType))
            }
        }
        _selectedFiles.value = current
    }

    fun removeSelectedFile(file: SelectedFile) {
        val current = _selectedFiles.value.toMutableList()
        current.remove(file)
        _selectedFiles.value = current
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun sendToPeer(peer: PeerDevice) {
        val files = _selectedFiles.value
        if (files.isEmpty()) return
        p2pClient.sendFiles(
            targetHost = peer.hostAddress,
            targetPort = peer.port,
            peerDeviceName = peer.name,
            files = files
        )
    }

    fun sendToIp(ip: String, port: Int = NetworkUtils.DEFAULT_PORT, name: String = "Direct Device") {
        val files = _selectedFiles.value
        if (files.isEmpty()) return
        p2pClient.sendFiles(
            targetHost = ip.trim(),
            targetPort = port,
            peerDeviceName = name,
            files = files
        )
    }

    fun onQrScanned(payload: QrPayload) {
        sendToIp(payload.ip, payload.port, payload.deviceName)
    }

    fun cancelActiveSend() {
        p2pClient.cancelActiveTransfer()
    }

    fun cancelActiveReceive() {
        p2pServer.cancelActiveTransfer()
    }

    fun resetSendProgress() {
        p2pClient.resetProgress()
    }

    fun resetReceiveProgress() {
        p2pServer.resetProgress()
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        nsdHelper?.destroy()
        p2pServer.stop()
        p2pClient.cancelActiveTransfer()
    }
}
