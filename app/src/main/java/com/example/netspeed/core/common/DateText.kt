package com.example.netspeed.core.common

import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Dates in the app language. Persian uses the Persian (Jalali) calendar and Persian digits. */
object DateText {
    private fun loc(): ULocale = if (Format.persian) ULocale("fa_IR") else ULocale.forLocale(Locale.getDefault())

    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Full date, e.g. "۲۲ آبان ۱۴۰۵". */
    fun date(ms: Long): String = SimpleDateFormat("d MMMM yyyy", loc()).format(Date(ms)).let { Format.d(it) }

    /** Time of day, e.g. "۱۴:۳۰". */
    fun time(ms: Long): String = SimpleDateFormat("HH:mm", loc()).format(Date(ms)).let { Format.d(it) }
}
