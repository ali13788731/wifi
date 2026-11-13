package com.example.netspeed.core.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.netspeed.BuildConfig
import com.example.netspeed.MainActivity
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Looks for a newer version and shows ONE notification per new version, with its release notes.
 *
 * The source is a tiny JSON file ([BuildConfig.UPDATE_URL], see update.json in the repo root):
 *   { "versionCode": 6, "versionName": "2.6.0", "url": "https://cafebazaar.ir/app/<package>",
 *     "notes": { "fa": "…", "en": "…" } }
 * A notification appears when versionCode is higher than the installed one.
 */
object UpdateChecker {
    private const val PERIODIC = "update-check"
    private const val NOW = "update-check-now"
    private const val ID = 7002

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    private val net get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Every 12 hours in the background (even when the app is closed). */
    fun schedule(ctx: Context) {
        if (BuildConfig.UPDATE_URL.isBlank()) return
        val req = PeriodicWorkRequestBuilder<Worker>(12, TimeUnit.HOURS).setConstraints(net).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** One extra check right now (called when the app is opened). */
    fun checkNow(ctx: Context) {
        if (BuildConfig.UPDATE_URL.isBlank()) return
        val req = OneTimeWorkRequestBuilder<Worker>().setConstraints(net).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
    }

    private class Info(val code: Int, val name: String, val url: String, val notes: String)

    private fun fetch(ctx: Context): Info? = runCatching {
        client.newCall(Request.Builder().url(BuildConfig.UPDATE_URL).header("Cache-Control", "no-cache").build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val j = JSONObject(r.body?.string() ?: return null)
            val n = j.opt("notes")
            val lang = if (Format.persian) "fa" else "en"
            val notes = when (n) {
                is JSONObject -> n.optString(lang).ifBlank { n.optString("en") }.ifBlank { n.optString("fa") }
                is String -> n
                else -> ""
            }
            Info(
                code = j.getInt("versionCode"),
                name = j.optString("versionName"),
                url = j.optString("url").ifBlank { "https://cafebazaar.ir/app/${ctx.packageName}" },
                notes = notes.trim()
            )
        }
    }.getOrNull()

    class Worker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val ctx = applicationContext
            val l = MainActivity.wrapLocale(ctx)          // also sets Format.persian for the notes language
            val info = fetch(ctx) ?: return@withContext Result.success()   // offline / bad file: try next time
            val prefs = ctx.getSharedPreferences("upd", Context.MODE_PRIVATE)
            if (info.code <= BuildConfig.VERSION_CODE || info.code <= prefs.getInt("notified", 0)) return@withContext Result.success()
            if (!Notify.canPost(ctx)) return@withContext Result.success()

            val tap = PendingIntent.getActivity(
                ctx, 1, Intent(Intent.ACTION_VIEW, Uri.parse(info.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val ver = info.name.ifBlank { info.code.toString() }
            val notes = info.notes.ifBlank { l.getString(R.string.upd_no_notes) }
            Notify.post(
                ctx, l, ID, Notify.CH_UPDATE,
                title = l.getString(R.string.upd_title, ver),
                short = notes.lineSequence().firstOrNull { it.isNotBlank() } ?: notes,
                big = notes,
                tap = tap, actionLabel = l.getString(R.string.upd_action)
            )
            prefs.edit().putInt("notified", info.code).apply()    // one notification per version
            Result.success()
        }
    }
}
