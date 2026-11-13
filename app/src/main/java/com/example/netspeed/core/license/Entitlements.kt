package com.example.netspeed.core.license

import com.example.netspeed.core.common.Settings
import com.example.netspeed.core.downloader.RequestOptions

/**
 * What the free version can and cannot do. Applied in the download engine (not only in the UI),
 * so a stored value from an older install or a hacked UI can never exceed the free limits.
 *
 * FREE : unlimited downloads, ONE network at a time (Wi-Fi first, mobile data as fallback),
 *        resume, queue, history, notifications, status-bar speed, themes, 2 parallel downloads,
 *        up to 4 connections per file.
 * PRO  : Wi-Fi + mobile data together for one file, speed limiter, monthly mobile-data cap,
 *        scheduled start, custom User-Agent / Referer / Cookies / login, usage statistics,
 *        up to 5 parallel downloads and 8 connections.
 */
object Entitlements {
    const val FREE_MAX_CONCURRENT = 2
    const val PRO_MAX_CONCURRENT = 5
    const val FREE_MAX_CONNECTIONS = 4
    const val PRO_MAX_CONNECTIONS = 8

    fun maxConcurrent(pro: Boolean) = if (pro) PRO_MAX_CONCURRENT else FREE_MAX_CONCURRENT
    fun maxConnections(pro: Boolean) = if (pro) PRO_MAX_CONNECTIONS else FREE_MAX_CONNECTIONS

    /** Settings the engine really uses. */
    fun limit(s: Settings, pro: Boolean): Settings =
        if (pro) s else s.copy(
            maxConcurrent = s.maxConcurrent.coerceIn(1, FREE_MAX_CONCURRENT),
            connectionsPerNetwork = s.connectionsPerNetwork.coerceIn(1, FREE_MAX_CONNECTIONS),
            speedLimitBps = 0,
            mobileLimitBytes = 0,
            mobileWarning = false,
            allowSingleNetwork = true          // free = one network is always enough
        )

    /** Request options the engine really uses: custom headers / login are Pro. */
    fun limit(o: RequestOptions, pro: Boolean): RequestOptions =
        if (pro) o else RequestOptions()

    /** Free users may not schedule a download for later. */
    fun scheduledAt(at: Long, pro: Boolean): Long = if (pro) at else 0L
}
