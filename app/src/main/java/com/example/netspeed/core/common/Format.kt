package com.example.netspeed.core.common

import java.util.Locale

object Format {
    /** True while the app language is Persian: digits, decimal point and units are written in Persian. */
    @Volatile var persian: Boolean = false

    private const val K = 1024.0
    private const val FA_ZERO = '\u06F0'

    /** Latin digits -> Persian digits (and "." -> "٫") when the language is Persian. */
    fun d(s: String): String {
        if (!persian) return s
        val b = StringBuilder(s.length)
        for (ch in s) b.append(
            when (ch) {
                in '0'..'9' -> FA_ZERO + (ch - '0')
                '.' -> '\u066B'
                else -> ch
            }
        )
        return b.toString()
    }

    /** A whole number in the current language. */
    fun n(v: Long): String = d(v.toString())
    fun n(v: Int): String = d(v.toString())

    /** "42%" or "۴۲٪". */
    fun pct(v: Int): String = d(v.toString()) + if (persian) "٪" else "%"

    private fun num(v: Double, decimals: Int): String = d(String.format(Locale.US, "%.${decimals}f", v))

    private val unitsEn = arrayOf("B", "KB", "MB", "GB", "TB")
    private val unitsFa = arrayOf("بایت", "کیلوبایت", "مگابایت", "گیگابایت", "ترابایت")
    private fun units() = if (persian) unitsFa else unitsEn
    private fun perSec() = if (persian) "/ثانیه" else "/s"

    fun bytes(b: Long): String {
        if (b < 0) return "—"
        val u = units()
        var v = b.toDouble(); var i = 0
        while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }
        return if (i == 0) "${d(b.toString())} ${u[0]}" else "${num(v, 2)} ${u[i]}"
    }

    /** Speed with the right unit: KB, MB, GB or TB per second (written out in Persian). */
    fun speed(bps: Long): String {
        val v = bps.coerceAtLeast(0).toDouble()
        val u = units()
        val (n, i) = when {
            v >= K * K * K * K -> (v / (K * K * K * K)) to 4
            v >= K * K * K -> (v / (K * K * K)) to 3
            v >= K * K -> (v / (K * K)) to 2
            else -> (v / K) to 1
        }
        return (if (i == 1) num(n, 0) else num(n, 2)) + " " + u[i] + perSec()
    }

    /**
     * Very short speed for the tiny status-bar icon: number + one unit letter.
     * English: K / M / G / T. Persian: ک (کیلو) / م (مگا) / گ (گیگا) / ت (ترا).
     */
    fun iconSpeed(bps: Long): String {
        val v = bps.coerceAtLeast(0).toDouble()
        val (n, i) = when {
            v >= K * K * K * K -> (v / (K * K * K * K)) to 3
            v >= K * K * K -> (v / (K * K * K)) to 2
            v >= K * K -> (v / (K * K)) to 1
            else -> (v / K) to 0
        }
        val letter = (if (persian) arrayOf("ک", "م", "گ", "ت") else arrayOf("K", "M", "G", "T"))[i]
        val raw = if (n >= 10) String.format(Locale.US, "%.0f", n) else String.format(Locale.US, "%.1f", n)
        return d(raw.removeSuffix(".0")) + letter
    }

    fun eta(sec: Long): String {
        if (sec < 0) return "—"
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return d(if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, s) else "%d:%02d".format(Locale.US, m, s))
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
