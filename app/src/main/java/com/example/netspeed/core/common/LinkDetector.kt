package com.example.netspeed.core.common

/**
 * Finds a link in a piece of text and decides whether it points to a downloadable FILE
 * (so a copied web-page address such as google.com does not pop up the download window).
 * Pure Kotlin: no Android classes, easy to unit-test.
 */
object LinkDetector {
    private val urlRx = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)

    private val fileExt = setOf(
        // video / audio / images
        "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "m4v", "ts",
        "mp3", "flac", "wav", "aac", "ogg", "m4a", "opus", "wma",
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "tiff", "svg", "heic",
        // archives / disk images / apps
        "zip", "rar", "7z", "tar", "gz", "xz", "bz2", "tgz", "zst", "iso", "img", "dmg",
        "apk", "xapk", "aab", "exe", "msi", "deb", "rpm", "appimage", "bin", "jar",
        // documents / data
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "mobi", "csv", "json", "xml",
        "torrent", "srt", "ttf", "otf", "psd", "sql", "db"
    )

    private val hints = listOf(
        "/download", "download=", "dl=1", "export=download", "attachment", "response-content-disposition",
        "/dl/", "/files/", "/get/", "/file/"
    )

    /** First http(s) link in [text], with trailing punctuation removed; null if none. */
    fun extract(text: String?): String? {
        val raw = text?.let { urlRx.find(it)?.value } ?: return null
        return raw.trimEnd('.', ',', ')', ']', '}', ';', ':', '!', '?', '،', '؛').takeIf { it.length > 10 }
    }

    private fun extOf(segment: String): String =
        segment.substringAfterLast('/').substringBefore('#').substringAfterLast('.', "").lowercase()

    /** True when the link looks like a direct file (known extension, or a download-style URL). */
    fun looksLikeFile(url: String): Boolean {
        val noFrag = url.substringBefore('#')
        val path = noFrag.substringBefore('?').substringAfter("://").substringAfter('/', "")
        val query = noFrag.substringAfter('?', "")
        if (extOf(path) in fileExt) return true
        // ?file=name.zip  /  &filename=name.mp4
        query.split('&').forEach { kv ->
            val v = kv.substringAfter('=', "").substringBefore('&')
            if (v.contains('.') && extOf(v) in fileExt) return true
        }
        val low = noFrag.lowercase()
        return hints.any { low.contains(it) }
    }
}
