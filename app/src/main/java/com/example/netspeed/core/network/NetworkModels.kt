package com.example.netspeed.core.network

import android.net.Network

/** The two physical transports the download engine routes over. */
enum class NetKind { WIFI, MOBILE }

/**
 * State of one transport. The four flags are deliberately separate:
 *  - [available]  Android has handed us a Network object for this transport
 *  - [connected]  the Network currently reports the transport + INTERNET capability
 *  - [validated]  Android verified real internet reachability (NET_CAPABILITY_VALIDATED)
 *  - "actively transferring" is tracked by the download engine, not here.
 */
data class NetState(
    val kind: NetKind,
    val available: Boolean = false,
    val connected: Boolean = false,
    val validated: Boolean = false,
    val network: Network? = null,
    val ssid: String? = null,
    val metered: Boolean = false
) {
    /** True when it is safe to route download traffic over this transport. */
    val usable: Boolean get() = available && connected && network != null
}

data class DualState(val wifi: NetState, val mobile: NetState) {
    operator fun get(kind: NetKind) = if (kind == NetKind.WIFI) wifi else mobile
    val dualReady: Boolean get() = wifi.usable && mobile.usable
}
