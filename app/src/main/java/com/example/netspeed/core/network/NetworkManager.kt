package com.example.netspeed.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers and tracks the Wi-Fi and cellular [Network] objects.
 *
 * Two kinds of callbacks are used:
 *  - passive (registerNetworkCallback): always on, only observes. Never forces a radio up.
 *  - active  (requestNetwork): held only while a download is running (see [acquire]).
 *    This is what asks Android to bring cellular up/keep it up while Wi-Fi is the default.
 *
 * Android does not let an app enable mobile data itself; if the user has it off,
 * the cellular state simply stays unavailable until they turn it on.
 */
@Singleton
class NetworkManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _state = MutableStateFlow(
        DualState(NetState(NetKind.WIFI), NetState(NetKind.MOBILE))
    )
    val state: StateFlow<DualState> = _state.asStateFlow()

    private val passive = mutableListOf<ConnectivityManager.NetworkCallback>()
    private val active = mutableListOf<ConnectivityManager.NetworkCallback>()
    private var leases = 0
    private var monitoring = false

    private fun transportOf(kind: NetKind) =
        if (kind == NetKind.WIFI) NetworkCapabilities.TRANSPORT_WIFI
        else NetworkCapabilities.TRANSPORT_CELLULAR

    private fun request(kind: NetKind) = NetworkRequest.Builder()
        .addTransportType(transportOf(kind))
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    private fun update(kind: NetKind, f: (NetState) -> NetState) {
        _state.update { s ->
            if (kind == NetKind.WIFI) s.copy(wifi = f(s.wifi)) else s.copy(mobile = f(s.mobile))
        }
    }

    private fun callbackFor(kind: NetKind) = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            update(kind) { it.copy(available = true, network = network) }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            update(kind) {
                it.copy(
                    network = network,
                    available = true,
                    connected = caps.hasTransport(transportOf(kind)) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                    ssid = if (kind == NetKind.WIFI) ssidOf(caps) else null
                )
            }
        }

        override fun onLost(network: Network) {
            update(kind) {
                if (it.network == network) NetState(kind) else it
            }
        }
    }

    private fun ssidOf(caps: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val raw = (caps.transportInfo as? WifiInfo)?.ssid ?: return null
        // Without location permission Android returns "<unknown ssid>".
        return raw.trim('"').takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    /** Start passive observation. Safe to call many times. */
    @Synchronized
    fun startMonitoring() {
        if (monitoring) return
        monitoring = true
        NetKind.values().forEach { kind ->
            val cb = callbackFor(kind)
            try {
                cm.registerNetworkCallback(request(kind), cb)
                passive += cb
            } catch (_: SecurityException) {
            }
        }
    }

    /**
     * Ask Android to keep both transports up for a download. Reference counted;
     * every call must be paired with [release].
     */
    @Synchronized
    fun acquire() {
        startMonitoring()
        if (leases++ > 0) return
        NetKind.values().forEach { kind ->
            val cb = callbackFor(kind)
            try {
                cm.requestNetwork(request(kind), cb)
                active += cb
            } catch (_: SecurityException) {
            } catch (_: RuntimeException) {
                // e.g. too many outstanding requests
            }
        }
    }

    @Synchronized
    fun release() {
        if (leases == 0) return
        if (--leases > 0) return
        active.forEach { runCatching { cm.unregisterNetworkCallback(it) } }
        active.clear()
    }
}
