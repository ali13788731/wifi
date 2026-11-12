package com.example.netspeed.core.downloader

import java.util.concurrent.atomic.AtomicLong

/** Counts bytes actually read from the socket of one network; speed = real byte delta. */
class SpeedMeter {
    private val total = AtomicLong(0)
    @Volatile private var lastBytes = 0L
    @Volatile private var lastNanos = System.nanoTime()
    @Volatile private var speed = 0.0

    fun add(n: Long) { total.addAndGet(n) }
    val bytes: Long get() = total.get()

    /**
     * Call about once per second. Returns the REAL byte rate of the last interval, with no
     * smoothing, so when a network stops delivering bytes the number falls to 0 right away.
     */
    @Synchronized
    fun sample(): Long {
        val now = System.nanoTime()
        val cur = total.get()
        val dt = (now - lastNanos) / 1e9
        if (dt >= 0.4) {
            speed = (cur - lastBytes) / dt
            lastBytes = cur
            lastNanos = now
        }
        return speed.toLong()
    }

    fun reset(initial: Long = 0) {
        total.set(initial); lastBytes = initial; lastNanos = System.nanoTime(); speed = 0.0
    }
}

/** Shared bytes/second cap across all connections of a download. 0 = unlimited. */
class RateLimiter(@Volatile var bytesPerSec: Long) {
    private var windowStart = System.nanoTime()
    private var used = 0L

    /** Blocks the calling worker long enough to respect the limit. */
    fun acquire(n: Int) {
        val limit = bytesPerSec
        if (limit <= 0) return
        var sleepMs = 0L
        synchronized(this) {
            val now = System.nanoTime()
            if (now - windowStart >= 1_000_000_000L) { windowStart = now; used = 0 }
            used += n
            if (used > limit) {
                sleepMs = ((windowStart + 1_000_000_000L - now) / 1_000_000L).coerceAtLeast(1)
                windowStart = now + sleepMs * 1_000_000L; used = 0
            }
        }
        if (sleepMs > 0) Thread.sleep(sleepMs)
    }
}
