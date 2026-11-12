package com.example.netspeed

import com.example.netspeed.core.downloader.RangeProbe
import com.example.netspeed.core.downloader.RequestOptions
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real HTTP against a local MockWebServer: 200, 206, 401, 416, redirect, Range unsupported. */
class RangeProbeTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val total = 100_000L

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun rangeServer(support: Boolean, headLen: Long = total) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(r: RecordedRequest): MockResponse {
                if (r.method == "HEAD") return MockResponse().setHeader("Content-Length", headLen)
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("Content-Disposition", "attachment; filename=\"movie.mkv\"")
                val range = r.getHeader("Range")
                if (!support || range == null) return MockResponse().setBody(Buffer().write(ByteArray(2048)))
                val m = Regex("bytes=(\\d+)-(\\d+)").matchEntire(range)!!
                val s = m.groupValues[1].toLong(); val e = m.groupValues[2].toLong()
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $s-$e/$total")
                    .setBody(Buffer().write(ByteArray((e - s + 1).toInt())))
            }
        }
    }

    @Test fun verifiesRealRange() {
        rangeServer(true)
        val p = RangeProbe.probe(client, server.url("/f").toString(), RequestOptions())
        assertTrue(p.message, p.rangeVerified)
        assertTrue(p.canMultiPart)
        assertEquals(total, p.totalBytes)
        assertEquals("movie.mkv", p.fileName)
    }

    @Test fun rangeIgnoredMeansNoMultiPart() {
        rangeServer(false)
        val p = RangeProbe.probe(client, server.url("/f").toString(), RequestOptions())
        assertFalse(p.rangeVerified)
        assertFalse(p.canMultiPart)
    }

    @Test fun acceptRangesHeaderAloneIsNotTrusted() {
        rangeServer(false)   // HEAD says "Accept-Ranges: bytes" but GET returns 200
        val p = RangeProbe.probe(client, server.url("/f").toString(), RequestOptions())
        assertFalse(p.canMultiPart)
    }

    @Test fun headAndContentRangeDisagreeIsRejected() {
        rangeServer(true, headLen = total + 5)
        val p = RangeProbe.probe(client, server.url("/f").toString(), RequestOptions())
        assertFalse(p.rangeVerified)
    }

    @Test fun authRequiredIsDetected() {
        server.enqueue(MockResponse().setResponseCode(401))
        val p = RangeProbe.probe(client, server.url("/f").toString(), RequestOptions())
        assertTrue(p.authRequired)
    }

    @Test fun http416IsReported() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(r: RecordedRequest) =
                if (r.method == "HEAD") MockResponse().setHeader("Content-Length", 10)
                else MockResponse().setResponseCode(416)
        }
        val p = RangeProbe.probe(client, server.url("/f").toString(), RequestOptions())
        assertFalse(p.rangeVerified)
        assertTrue(p.message.contains("416"))
    }

    @Test fun followsRedirect() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(r: RecordedRequest): MockResponse {
                if (r.path == "/start") return MockResponse().setResponseCode(302).setHeader("Location", "/real")
                if (r.method == "HEAD") return MockResponse().setHeader("Content-Length", total)
                val m = Regex("bytes=(\\d+)-(\\d+)").matchEntire(r.getHeader("Range") ?: "bytes=0-1023")!!
                val s = m.groupValues[1].toLong(); val e = m.groupValues[2].toLong()
                return MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $s-$e/$total")
                    .setBody(Buffer().write(ByteArray((e - s + 1).toInt())))
            }
        }
        val p = RangeProbe.probe(client, server.url("/start").toString(), RequestOptions())
        assertTrue(p.message, p.rangeVerified)
        assertTrue(p.finalUrl.endsWith("/real"))
    }

    @Test fun customHeadersAreSent() {
        rangeServer(true)
        RangeProbe.probe(client, server.url("/f").toString(),
            RequestOptions(userAgent = "UA-X", referer = "http://ref/", cookies = "a=b"))
        val r = server.takeRequest()
        assertEquals("UA-X", r.getHeader("User-Agent"))
        assertEquals("http://ref/", r.getHeader("Referer"))
        assertEquals("a=b", r.getHeader("Cookie"))
    }
}
