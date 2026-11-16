package com.karadesk.internal.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object NetworkMonitor {
    private val online = MutableStateFlow(true)
    private var started = false

    val isOnline: StateFlow<Boolean> = online.asStateFlow()

    fun start(context: Context) {
        if (started) {
            return
        }

        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return

        started = true
        online.value = manager.hasInternet(manager.activeNetwork)

        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    online.value = manager.hasInternet(network)
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }

                override fun onLost(network: Network) {
                    online.value = false
                }
            })
        }
    }

    private fun ConnectivityManager.hasInternet(network: Network?): Boolean {
        val capabilities = network?.let(::getNetworkCapabilities) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
