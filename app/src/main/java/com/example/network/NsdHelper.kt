package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.example.model.PeerDevice
import java.net.InetAddress

class NsdHelper(
    private val context: Context,
    private val onPeerDiscovered: (PeerDevice) -> Unit,
    private val onPeerLost: (String) -> Unit
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false
    private var isRegistered = false

    private val discoveredPeers = mutableMapOf<String, PeerDevice>()

    init {
        acquireMulticastLock()
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("BeamDropMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release multicast lock: ${e.message}")
        }
    }

    fun registerService(port: Int, customName: String? = null) {
        if (isRegistered || nsdManager == null) return
        val serviceName = customName ?: NetworkUtils.getDeviceName()

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = NetworkUtils.SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${NsdServiceInfo.serviceName}")
                isRegistered = true
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service registration failed: $errorCode")
                isRegistered = false
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered")
                isRegistered = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering NSD service: ${e.message}")
        }
    }

    fun unregisterService() {
        if (!isRegistered || nsdManager == null || registrationListener == null) return
        try {
            nsdManager.unregisterService(registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering service: ${e.message}")
        } finally {
            isRegistered = false
            registrationListener = null
        }
    }

    fun startDiscovery() {
        if (isDiscovering || nsdManager == null) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "NSD Service discovery started")
                isDiscovering = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${service.serviceName}")
                if (service.serviceType.contains("_beamdrop")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${service.serviceName}")
                val peerId = service.serviceName
                discoveredPeers.remove(peerId)
                onPeerLost(peerId)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped: $serviceType")
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(NetworkUtils.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting discovery: ${e.message}")
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                val host: InetAddress? = resolvedService.host
                val port = resolvedService.port
                val hostAddress = host?.hostAddress

                if (hostAddress != null) {
                    val localIp = NetworkUtils.getLocalIpAddress()
                    // Don't add own device as peer
                    if (hostAddress != localIp) {
                        val peer = PeerDevice(
                            id = resolvedService.serviceName,
                            name = resolvedService.serviceName,
                            hostAddress = hostAddress,
                            port = port
                        )
                        discoveredPeers[peer.id] = peer
                        onPeerDiscovered(peer)
                    }
                }
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error in resolveService: ${e.message}")
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering || nsdManager == null || discoveryListener == null) return
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery: ${e.message}")
        } finally {
            isDiscovering = false
            discoveryListener = null
        }
    }

    fun destroy() {
        stopDiscovery()
        unregisterService()
        releaseMulticastLock()
    }

    companion object {
        private const val TAG = "BeamDrop_NSD"
    }
}
