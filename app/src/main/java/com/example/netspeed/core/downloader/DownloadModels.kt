package com.example.netspeed.core.downloader

import com.example.netspeed.core.network.NetKind

/** Extra request data: custom UA, referer, cookies, headers, basic auth. */
data class RequestOptions(
    val userAgent: String = DEFAULT_UA,
    val referer: String? = null,
    val cookies: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val username: String? = null,
    val password: String? = null
) {
    companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14) DualNet/2.0"
    }
}

/** Result of the HTTP Range detection engine. */
data class ProbeResult(
    val finalUrl: String,
    val totalBytes: Long,            // -1 when unknown
    val rangeVerified: Boolean,      // a real Range request returned a valid 206
    val fileName: String?,
    val mimeType: String?,
    val etag: String?,
    val lastModified: String?,
    val authRequired: Boolean,
    val message: String,             // English explanation (logs); the UI localizes by [code]
    val code: String = "OK"          // OK, RANGE_UNSUPPORTED, RANGE_INVALID, AUTH, UNREACHABLE, HTTP_<n>
) {
    /** Multi-part and dual-network mode require a verified Range and a known size. */
    val canMultiPart: Boolean get() = rangeVerified && totalBytes > 0
}

enum class DownloadStatus { QUEUED, PROBING, RUNNING, PAUSED, VERIFYING, COMPLETED, FAILED, CANCELED }

enum class NetworkMode { DUAL, WIFI_ONLY, MOBILE_ONLY, AUTO }

enum class SegmentState { PENDING, RUNNING, DONE, FAILED }

/** Live per-network counters, used by the UI and by the diagnostics screen. */
data class NetStats(
    val kind: NetKind,
    val speedBps: Long = 0,
    val bytes: Long = 0,
    val activeConnections: Int = 0,
    val segments: List<Int> = emptyList()   // segment ids currently assigned
)

data class LiveProgress(
    val downloadId: Long,
    val status: DownloadStatus,
    val totalBytes: Long,
    val doneBytes: Long,
    val wifi: NetStats,
    val mobile: NetStats,
    val startedAt: Long = 0,
    /** Machine code shown localized in the UI: wifi_lost, mobile_lost, waiting_wifi, waiting_mobile,
     *  waiting_both, checking, mobile_limit, or null. */
    val notice: String? = null
) {
    val totalSpeedBps: Long get() = wifi.speedBps + mobile.speedBps
    val percent: Int get() = if (totalBytes > 0) ((doneBytes * 100) / totalBytes).toInt() else 0
    val remainingBytes: Long get() = (totalBytes - doneBytes).coerceAtLeast(0)
    val etaSeconds: Long get() = if (totalSpeedBps > 0) remainingBytes / totalSpeedBps else -1
}
