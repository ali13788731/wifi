package com.example.netspeed.core.common

import java.util.Locale

object Format {
    fun bytes(b: Long): String {
        if (b < 0) return "—"
        val u = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble(); var i = 0
        while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }
        return if (i == 0) "${b} B" else String.format(Locale.US, "%.2f %s", v, u[i])
    }

    fun speed(bps: Long): String {
        val mb = bps / (1024.0 * 1024.0)
        return if (mb >= 1) String.format(Locale.US, "%.2f MB/s", mb)
        else String.format(Locale.US, "%.0f KB/s", bps / 1024.0)
    }

    fun eta(sec: Long): String {
        if (sec < 0) return "—"
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun category(name: String, mime: String?): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp") || mime?.startsWith("video") == true -> "video"
            ext in setOf("mp3", "flac", "wav", "aac", "ogg", "m4a") || mime?.startsWith("audio") == true -> "audio"
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp") || mime?.startsWith("image") == true -> "image"
            ext in setOf("zip", "rar", "7z", "tar", "gz", "xz") -> "archive"
            ext in setOf("apk", "xapk", "aab") -> "app"
            ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub") -> "document"
            else -> "other"
        }
    }

    fun safeName(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trim('.').ifEmpty { "download.bin" }
}
