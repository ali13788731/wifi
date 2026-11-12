package com.example.netspeed.core.downloader

import com.example.netspeed.core.database.SegmentEntity

/** Pure functions: split a file into segments and verify they cover it exactly. */
object SegmentPlanner {
    private const val KIB = 1024L
    private const val MIB = 1024L * 1024L

    /**
     * Small segments are what make the allocation dynamic: every connection pulls the next
     * pending segment when it is free, so the faster network naturally takes more of them.
     */
    fun chunkSize(total: Long): Long = (total / 64).coerceIn(512 * KIB, 8 * MIB)

    fun plan(downloadId: Long, total: Long): List<SegmentEntity> {
        require(total > 0) { "total must be positive" }
        val size = chunkSize(total)
        val out = ArrayList<SegmentEntity>()
        var start = 0L
        var i = 0
        while (start < total) {
            val end = minOf(start + size - 1, total - 1)
            out += SegmentEntity(downloadId = downloadId, index = i++, startByte = start, endByte = end)
            start = end + 1
        }
        return out
    }

    /**
     * Returns null when the segment set is a valid, complete, gap-free, overlap-free cover of
     * [0, total) with every segment DONE and validated; otherwise a description of the problem.
     */
    fun verify(segments: List<SegmentEntity>, total: Long): String? {
        if (segments.isEmpty()) return "No segments recorded."
        val sorted = segments.sortedBy { it.startByte }
        var expectedStart = 0L
        var sum = 0L
        for (s in sorted) {
            if (s.startByte != expectedStart) return "Segment ${s.index} boundary mismatch (gap or overlap)."
            if (s.state != SegmentState.DONE.name) return "Segment ${s.index} is not complete."
            if (!s.validated) return "Segment ${s.index} failed validation."
            if (s.downloadedBytes != s.length) return "Segment ${s.index} has ${s.downloadedBytes}/${s.length} bytes."
            sum += s.length
            expectedStart = s.endByte + 1
        }
        if (expectedStart != total) return "Segments end at $expectedStart, expected $total."
        if (sum != total) return "Segment bytes $sum differ from file size $total."
        return null
    }
}
