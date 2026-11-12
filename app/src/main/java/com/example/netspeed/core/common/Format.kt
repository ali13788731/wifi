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

    private const val K = 1024.0

    /** Speed with the right unit: KB/s, MB/s, GB/s or TB/s. */
    fun speed(bps: Long): String {
        val v = bps.coerceAtLeast(0).toDouble()
        return when {
            v >= K * K * K * K -> String.format(Locale.US, "%.2f TB/s", v / (K * K * K * K))
            v >= K * K * K -> String.format(Locale.US, "%.2f GB/s", v / (K * K * K))
            v >= K * K -> String.format(Locale.US, "%.2f MB/s", v / (K * K))
            else -> String.format(Locale.US, "%.0f KB/s", v / K)
        }
    }

    /**
     * Very short speed for the tiny status-bar icon: number + one unit letter (K, M, G or T),
     * e.g. "850K", "9.5M", "12M", "1.2G".
     */
    fun iconSpeed(bps: Long): String {
        val v = bps.coerceAtLeast(0).toDouble()
        val (n, u) = when {
            v >= K * K * K * K -> (v / (K * K * K * K)) to "T"
            v >= K * K * K -> (v / (K * K * K)) to "G"
            v >= K * K -> (v / (K * K)) to "M"
            else -> (v / K) to "K"
        }
        val num = if (n >= 10) String.format(Locale.US, "%.0f", n) else String.format(Locale.US, "%.1f", n)
        return num.removeSuffix(".0") + u
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
