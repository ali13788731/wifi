package com.example.netspeed.core.common

import android.content.Context
import com.example.netspeed.R

/** Maps machine codes (errors, live notices) to precise, localized text. Used by UI and service. */
object Texts {

    fun error(ctx: Context, code: String?): String {
        val c = code ?: return ctx.getString(R.string.err_unknown)
        if (c.startsWith("HTTP_")) {
            val n = c.removePrefix("HTTP_")
            return when (n) {
                "404", "410" -> ctx.getString(R.string.err_http_404)
                "403" -> ctx.getString(R.string.err_http_403)
                else -> ctx.getString(R.string.err_http, Format.d(n))
            }
        }
        return ctx.getString(
            when (c) {
                "INVALID_URL" -> R.string.invalid_url
                "NO_WIFI" -> R.string.err_no_wifi
                "NO_MOBILE" -> R.string.err_no_mobile
                "NO_BOTH" -> R.string.err_no_both
                "AUTH" -> R.string.err_auth
                "UNREACHABLE" -> R.string.err_unreachable
                "RANGE_UNSUPPORTED" -> R.string.err_range_unsupported
                "RANGE_INVALID" -> R.string.err_range_invalid
                "FILE_CHANGED" -> R.string.err_file_changed
                "VERIFY_FAILED" -> R.string.err_verify
                "SAVE_FAILED" -> R.string.err_save
                "SEGMENT_FAILED" -> R.string.err_segment
                "MOBILE_LIMIT" -> R.string.err_mobile_limit
                "DUPLICATE" -> R.string.err_duplicate
                else -> R.string.err_unknown
            }
        )
    }

    /** Message for "cannot start / continue because a network is off". */
    fun blocked(ctx: Context, code: String?): String = ctx.getString(
        when (code) {
            "NO_WIFI" -> R.string.blocked_no_wifi
            "NO_MOBILE" -> R.string.blocked_no_mobile
            else -> R.string.blocked_no_both
        }
    )

    fun notice(ctx: Context, code: String?): String? = when (code) {
        "wifi_lost" -> R.string.notice_wifi_lost
        "mobile_lost" -> R.string.notice_mobile_lost
        "waiting_both" -> R.string.notice_waiting_both
        "waiting_wifi" -> R.string.notice_waiting_wifi
        "waiting_mobile" -> R.string.notice_waiting_mobile
        "checking" -> R.string.notice_checking
        "mobile_limit" -> R.string.notice_mobile_limit
        else -> null
    }?.let { ctx.getString(it) }
}
