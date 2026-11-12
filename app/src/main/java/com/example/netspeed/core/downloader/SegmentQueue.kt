package com.example.netspeed.core.downloader

import com.example.netspeed.core.database.SegmentEntity

/** Mutable runtime view of one segment. Fields are only changed under the owning queue's lock
 *  or by the single worker that currently owns the segment. */
class Seg(val e: SegmentEntity) {
    @Volatile var downloaded: Long = e.downloadedBytes
    @Volatile var state: SegmentState = SegmentState.valueOf(e.state)
    @Volatile var network: String? = e.network
    @Volatile var retries: Int = e.retryCount
    @Volatile var validated: Boolean = e.validated
    @Volatile var startTime: Long = e.startTime
    @Volatile var completionTime: Long = e.completionTime

    val length: Long get() = e.length
    val nextByte: Long get() = e.startByte + downloaded

    fun toEntity() = e.copy(
        downloadedBytes = downloaded, network = network, state = state.name,
        startTime = startTime, completionTime = completionTime,
        retryCount = retries, validated = validated
    )
}

/**
 * Shared pool of segments. Every connection of every network pulls from here, which is what
 * produces throughput-proportional allocation: a faster network frees its workers sooner and
 * therefore takes more segments. Failed segments are handed back so the other network (or the
 * same one after it recovers) finishes them.
 */
class SegmentQueue(segments: List<Seg>) {
    val all: List<Seg> = segments
    private val pending = ArrayDeque<Seg>()

    init {
        all.forEach {
            if (it.state == SegmentState.DONE && it.validated) return@forEach
            // anything that was RUNNING when the app died is simply pending again
            it.state = SegmentState.PENDING
            pending.addLast(it)
        }
    }

    @Synchronized
    fun take(): Seg? {
        val s = pending.removeFirstOrNull() ?: return null
        s.state = SegmentState.RUNNING
        if (s.startTime == 0L) s.startTime = System.currentTimeMillis()
        return s
    }

    @Synchronized
    fun giveBack(s: Seg) {
        s.state = SegmentState.PENDING
        pending.addFirst(s)
    }

    @Synchronized
    fun complete(s: Seg, kind: String) {
        s.state = SegmentState.DONE
        s.validated = true
        s.network = kind
        s.completionTime = System.currentTimeMillis()
    }

    @Synchronized
    fun allDone(): Boolean = all.all { it.state == SegmentState.DONE && it.validated }

    val doneBytes: Long get() = all.sumOf { it.downloaded }
}
