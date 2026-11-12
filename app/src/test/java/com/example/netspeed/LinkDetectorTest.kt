package com.example.netspeed

import com.example.netspeed.core.common.Format
import com.example.netspeed.core.common.LinkDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkDetectorTest {
    @Test fun extractsLinkFromText() {
        assertEquals("https://x.com/a.zip", LinkDetector.extract("download this: https://x.com/a.zip."))
        assertNull(LinkDetector.extract("no link here"))
    }

    @Test fun fileLinksAreRecognised() {
        assertTrue(LinkDetector.looksLikeFile("https://host.com/files/movie.mkv"))
        assertTrue(LinkDetector.looksLikeFile("https://host.com/a/b/app.apk?token=1"))
        assertTrue(LinkDetector.looksLikeFile("https://host.com/get?file=data.zip"))
        assertTrue(LinkDetector.looksLikeFile("https://host.com/x?download=1"))
    }

    @Test fun ordinaryWebPagesAreNot() {
        assertFalse(LinkDetector.looksLikeFile("https://www.google.com/"))
        assertFalse(LinkDetector.looksLikeFile("https://example.com/news/article-12"))
        assertFalse(LinkDetector.looksLikeFile("https://example.com/index.html"))
    }

    @Test fun statusBarIconSpeedHasUnit() {
        assertEquals("0K", Format.iconSpeed(0))
        assertEquals("850K", Format.iconSpeed(850L * 1024))
        assertEquals("1.5M", Format.iconSpeed((1.5 * 1024 * 1024).toLong()))
        assertEquals("12M", Format.iconSpeed(12L * 1024 * 1024))
        assertEquals("2G", Format.iconSpeed(2L * 1024 * 1024 * 1024))
        assertEquals("3T", Format.iconSpeed(3L * 1024 * 1024 * 1024 * 1024))
    }

    @Test fun speedTextGoesUpToTerabytes() {
        assertEquals("1.00 GB/s", Format.speed(1024L * 1024 * 1024))
        assertEquals("2.00 TB/s", Format.speed(2L * 1024 * 1024 * 1024 * 1024))
        assertEquals("512 KB/s", Format.speed(512L * 1024))
    }
}
