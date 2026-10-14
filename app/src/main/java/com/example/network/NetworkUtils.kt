package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    const val DEFAULT_PORT = 8988
    const val SERVICE_TYPE = "_beamdrop._tcp."

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    /**
     * Resolves the current local IPv4 address (Wi-Fi, Hotspot AP, or P2P interface).
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Prioritize Wi-Fi interfaces (wlan, ap, p2p)
            val sortedInterfaces = interfaces.sortedByDescending { ni ->
                when {
                    ni.name.startsWith("wlan", ignoreCase = true) -> 3
                    ni.name.startsWith("ap", ignoreCase = true) -> 2
                    ni.name.startsWith("p2p", ignoreCase = true) -> 1
                    else -> 0
                }
            }

            for (ni in sortedInterfaces) {
                if (ni.isLoopback || !ni.isUp) continue
                val addresses = Collections.list(ni.inetAddresses)
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress
                        if (hostAddress != null && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun isWifiOrHotspotActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun getConnectedWifiSsid(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifiManager?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "")
            if (ssid != null && ssid != "<unknown ssid>" && ssid.isNotBlank()) {
                ssid
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
