package com.example.netspeed.core.downloader

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder

/**
 * HTTP Range detection engine. It never trusts "Accept-Ranges" alone:
 *  1. HEAD  -> Content-Length, Accept-Ranges, redirects, Content-Disposition, auth
 *  2. GET with "Range: bytes=0-1023" -> must be HTTP 206, a Content-Range of
 *     "bytes 0-N/total" that agrees with HEAD, and exactly N+1 bytes in the body.
 */
object RangeProbe {

    private const val PROBE_BYTES = 1024L

    fun applyOptions(b: Request.Builder, o: RequestOptions): Request.Builder {
        b.header("User-Agent", o.userAgent)
        o.referer?.let { b.header("Referer", it) }
        o.cookies?.let { b.header("Cookie", it) }
        o.headers.forEach { (k, v) -> b.header(k, v) }
        if (!o.username.isNullOrEmpty()) {
            b.header("Authorization", Credentials.basic(o.username, o.password ?: ""))
        }
        return b
    }

    fun probe(client: OkHttpClient, url: String, o: RequestOptions): ProbeResult {
        var finalUrl = url
        var length = -1L
        var name: String? = null
        var mime: String? = null
        var etag: String? = null
        var lastMod: String? = null
        var acceptRanges = false

        // ---- 1. HEAD ----
        try {
            client.newCall(applyOptions(Request.Builder().url(url).head(), o).build())
                .execute().use { r ->
                    if (r.code == 401 || r.code == 407) return authNeeded(url)
                    if (r.isSuccessful) {
                        finalUrl = r.request.url.toString()
                        length = r.header("Content-Length")?.toLongOrNull() ?: -1L
                        acceptRanges = r.header("Accept-Ranges")?.contains("bytes", true) == true
                        name = fileNameOf(r)
                        mime = r.header("Content-Type")?.substringBefore(';')?.trim()
                        etag = r.header("ETag")
                        lastMod = r.header("Last-Modified")
                    }
                    // Some servers reject HEAD (405/403): the Range GET below still works.
                }
        } catch (e: IOException) {
            return fail(url, "Cannot reach server: ${e.message}")
        }

        // ---- 2. Real Range request ----
        val req = applyOptions(Request.Builder().url(finalUrl), o)
            .header("Range", "bytes=0-${PROBE_BYTES - 1}")
            .header("Accept-Encoding", "identity")
            .build()
        try {
            client.newCall(req).execute().use { r ->
                if (r.code == 401 || r.code == 407) return authNeeded(finalUrl)
                if (r.code == 416) return fail(finalUrl, "Server answered HTTP 416 to a valid range.")
                if (r.code != 206) {
                    val total = r.header("Content-Length")?.toLongOrNull() ?: length
                    return ProbeResult(
                        r.request.url.toString(), total, false,
                        name ?: fileNameOf(r), mime ?: r.header("Content-Type")?.substringBefore(';'),
                        etag ?: r.header("ETag"), lastMod ?: r.header("Last-Modified"), false,
                        "Server does not support HTTP Range (answered ${r.code}). " +
                            "Dual-network and multi-part are disabled; a normal single download is used."
                    )
                }
                val cr = r.header("Content-Range")
                    ?: return unverified(finalUrl, length, name, mime, etag, lastMod,
                        "HTTP 206 without Content-Range header.")
                val m = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""").matchEntire(cr.trim())
                    ?: return unverified(finalUrl, length, name, mime, etag, lastMod,
                        "Malformed Content-Range: $cr")
                val start = m.groupValues[1].toLong()
                val end = m.groupValues[2].toLong()
                val total = m.groupValues[3].toLongOrNull() ?: -1L
                if (start != 0L) return unverified(finalUrl, length, name, mime, etag, lastMod,
                    "Content-Range started at $start, expected 0.")
                if (total <= 0) return unverified(finalUrl, length, name, mime, etag, lastMod,
                    "Total size unknown in Content-Range.")
                if (length > 0 && length != total) return unverified(finalUrl, length, name, mime, etag, lastMod,
                    "HEAD length $length disagrees with Content-Range total $total.")

                // Read the body and check the byte count really matches the range.
                val expected = end - start + 1
                var got = 0L
                val buf = ByteArray(2048)
                r.body!!.byteStream().use { s ->
                    while (true) {
                        val n = s.read(buf)
                        if (n < 0) break
                        got += n
                    }
                }
                if (got != expected) return unverified(finalUrl, total, name, mime, etag, lastMod,
                    "Range body had $got bytes, expected $expected.")

                return ProbeResult(
                    r.request.url.toString(), total, true,
                    name ?: fileNameOf(r), mime ?: r.header("Content-Type")?.substringBefore(';')?.trim(),
                    etag ?: r.header("ETag"), lastMod ?: r.header("Last-Modified"), false,
                    "HTTP Range verified (206, Content-Range $cr)."
                )
            }
        } catch (e: IOException) {
            return fail(finalUrl, "Range test failed: ${e.message}")
        }
    }

    private fun unverified(
        url: String, len: Long, name: String?, mime: String?, etag: String?, lm: String?, why: String
    ) = ProbeResult(url, len, false, name, mime, etag, lm, false,
        "$why Dual-network is disabled for this file; a normal single download is used.")

    private fun authNeeded(url: String) = ProbeResult(
        url, -1, false, null, null, null, null, true,
        "The server requires authentication (HTTP 401/407). Enter a username and password."
    )

    private fun fail(url: String, msg: String) =
        ProbeResult(url, -1, false, null, null, null, null, false, msg)

    /** Content-Disposition filename, falling back to the last URL path segment. */
    fun fileNameOf(r: Response): String? {
        val cd = r.header("Content-Disposition")
        if (cd != null) {
            Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
                .find(cd)?.groupValues?.get(1)?.let {
                    return runCatching { URLDecoder.decode(it.trim().trim('"'), "UTF-8") }.getOrNull()
                }
            Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
                .find(cd)?.groupValues?.get(1)?.let { return it.trim() }
        }
        return r.request.url.pathSegments.lastOrNull { it.isNotBlank() }
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
    }
}
