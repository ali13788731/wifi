package com.example.netspeed

import com.example.netspeed.core.common.Settings
import com.example.netspeed.core.downloader.RequestOptions
import com.example.netspeed.core.license.Entitlements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {
    private val maxed = Settings(
        maxConcurrent = 5, connectionsPerNetwork = 8, speedLimitBps = 1_000_000,
        mobileLimitBytes = 500_000_000, mobileWarning = true, allowSingleNetwork = false
    )

    @Test fun freeSettingsAreCapped() {
        val s = Entitlements.limit(maxed, pro = false)
        assertEquals(Entitlements.FREE_MAX_CONCURRENT, s.maxConcurrent)
        assertEquals(Entitlements.FREE_MAX_CONNECTIONS, s.connectionsPerNetwork)
        assertEquals(0L, s.speedLimitBps)
        assertEquals(0L, s.mobileLimitBytes)
        assertFalse(s.mobileWarning)
        assertTrue("free = one network is enough", s.allowSingleNetwork)
    }

    @Test fun proSettingsAreUntouched() {
        assertEquals(maxed, Entitlements.limit(maxed, pro = true))
    }

    @Test fun freeIgnoresCustomRequestOptions() {
        val o = RequestOptions(userAgent = "x", referer = "r", cookies = "c", username = "u", password = "p")
        val f = Entitlements.limit(o, pro = false)
        assertEquals(RequestOptions.DEFAULT_UA, f.userAgent)
        assertNull(f.referer); assertNull(f.cookies); assertNull(f.username); assertNull(f.password)
        assertEquals(o, Entitlements.limit(o, pro = true))
    }

    @Test fun schedulingIsProOnly() {
        assertEquals(0L, Entitlements.scheduledAt(123456L, pro = false))
        assertEquals(123456L, Entitlements.scheduledAt(123456L, pro = true))
    }

    @Test fun limitsByTier() {
        assertEquals(2, Entitlements.maxConcurrent(false)); assertEquals(5, Entitlements.maxConcurrent(true))
        assertEquals(4, Entitlements.maxConnections(false)); assertEquals(8, Entitlements.maxConnections(true))
    }
}
