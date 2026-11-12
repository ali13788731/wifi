package com.example.netspeed.feature.history

import com.example.netspeed.core.database.DownloadEntity
import java.util.Calendar

enum class TimeRange { ALL, TODAY, WEEK, MONTH, YEAR }
enum class SizeBucket { ANY, SMALL, MEDIUM, LARGE, HUGE }
enum class NetBias { ANY, WIFI_MAJOR, MOBILE_MAJOR }
enum class SortBy { NEWEST, OLDEST, LARGEST, SMALLEST, NAME }

data class HistoryFilter(
    val query: String = "",
    val categories: Set<String> = emptySet(),
    val status: String = "ALL",             // ALL / COMPLETED / FAILED / CANCELED
    val range: TimeRange = TimeRange.ALL,
    val size: SizeBucket = SizeBucket.ANY,
    val bias: NetBias = NetBias.ANY,
    val sort: SortBy = SortBy.NEWEST
) {
    /** How many non-default filters are active (shown as a badge). */
    val activeCount: Int get() = listOf(
        categories.isNotEmpty(), status != "ALL", range != TimeRange.ALL,
        size != SizeBucket.ANY, bias != NetBias.ANY, sort != SortBy.NEWEST
    ).count { it }
}

fun startOf(range: TimeRange): Long {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return when (range) {
        TimeRange.ALL -> 0L
        TimeRange.TODAY -> c.timeInMillis
        TimeRange.WEEK -> c.apply { add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis
        TimeRange.MONTH -> c.apply { add(Calendar.DAY_OF_YEAR, -29) }.timeInMillis
        TimeRange.YEAR -> c.apply { add(Calendar.DAY_OF_YEAR, -364) }.timeInMillis
    }
}

private const val MB = 1024L * 1024L

/** When a download finished (or was created, if it never did). */
val DownloadEntity.time: Long get() = if (completedAt > 0) completedAt else createdAt

fun applyFilter(all: List<DownloadEntity>, f: HistoryFilter): List<DownloadEntity> {
    val since = startOf(f.range)
    val q = f.query.trim().lowercase()
    val out = all.filter { e ->
        e.status in setOf("COMPLETED", "FAILED", "CANCELED") &&
            (f.status == "ALL" || e.status == f.status) &&
            (q.isEmpty() || e.fileName.lowercase().contains(q) || e.url.lowercase().contains(q)) &&
            (f.categories.isEmpty() || e.category in f.categories) &&
            e.time >= since &&
            when (f.size) {
                SizeBucket.ANY -> true
                SizeBucket.SMALL -> e.totalBytes < 10 * MB
                SizeBucket.MEDIUM -> e.totalBytes in (10 * MB) until (100 * MB)
                SizeBucket.LARGE -> e.totalBytes in (100 * MB) until (1024 * MB)
                SizeBucket.HUGE -> e.totalBytes >= 1024 * MB
            } &&
            when (f.bias) {
                NetBias.ANY -> true
                NetBias.WIFI_MAJOR -> e.wifiBytes > e.mobileBytes
                NetBias.MOBILE_MAJOR -> e.mobileBytes > e.wifiBytes
            }
    }
    return when (f.sort) {
        SortBy.NEWEST -> out.sortedByDescending { it.time }
        SortBy.OLDEST -> out.sortedBy { it.time }
        SortBy.LARGEST -> out.sortedByDescending { it.totalBytes }
        SortBy.SMALLEST -> out.sortedBy { it.totalBytes }
        SortBy.NAME -> out.sortedBy { it.fileName.lowercase() }
    }
}
