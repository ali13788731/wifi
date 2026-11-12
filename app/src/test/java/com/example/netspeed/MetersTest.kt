package com.example.netspeed

import com.example.netspeed.core.common.Format
import com.example.netspeed.core.downloader.RateLimiter
import com.example.netspeed.core.downloader.SpeedMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetersTest {
    @Test fun speedMeterReflectsRealBytes() {
        val m = SpeedMeter()
        m.sample()
        m.add(2_000_000)
        Thread.sleep(500)
        val s = m.sample()
        assertTrue("speed $s", s in 2_000_000L..6_000_000L)
        assertEquals(2_000_000L, m.bytes)
    }

    @Test fun rateLimiterThrottles() {
        val rl = RateLimiter(100_000)
        val t0 = System.nanoTime()
        repeat(5) { rl.acquire(50_000) }              // 250 kB at 100 kB/s => ~2.5 s
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("took $ms ms", ms in 2300L..4000L)
    }

    @Test fun unlimitedDoesNotBlock() {
        val rl = RateLimiter(0)
        val t0 = System.nanoTime()
        repeat(1000) { rl.acquire(1_000_000) }
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 500)
    }

    @Test fun formatting() {
        assertEquals("1.00 MB/s", Format.speed(1024 * 1024))
        assertEquals("video", Format.category("a.mkv", null))
        assertEquals("a_b.txt", Format.safeName("a/b.txt"))
    }
}
