package com.example.netspeed

import com.example.netspeed.core.common.Format
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatFaTest {
    @After fun reset() { Format.persian = false }

    @Test fun persianUnitsAndDigits() {
        Format.persian = true
        assertEquals("۱٫۰۰ مگابایت/ثانیه", Format.speed(1024 * 1024))
        assertEquals("۲٫۰۰ کیلوبایت", Format.bytes(2048))
        assertEquals("۴۲٪", Format.pct(42))
        assertEquals("۱:۰۵", Format.eta(65))
        assertEquals("۹٫۵م", Format.iconSpeed((9.5 * 1024 * 1024).toLong()))
    }
}
