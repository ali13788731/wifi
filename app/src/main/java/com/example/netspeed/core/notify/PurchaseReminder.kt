package com.example.netspeed.core.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.netspeed.MainActivity
import com.example.netspeed.R
import java.util.concurrent.TimeUnit

/**
 * "Pro is available" reminder for free users (the app itself is free and never blocked).
 *
 * Scheduled only after Bazaar CONFIRMED that this account does not own the product
 * ([onUnlicensed], called from LicenseManager) and cancelled as soon as it is bought ([onLicensed]).
 * It never spams: at most [MAX_REMINDERS] notifications, one per [EVERY_HOURS] hours.
 */
object PurchaseReminder {
    private const val WORK = "purchase-reminder"
    private const val EVERY_HOURS = 72L
    const val MAX_REMINDERS = 3
    private const val ID = 7001

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lic", Context.MODE_PRIVATE)

    fun onUnlicensed(ctx: Context) {
        prefs(ctx).edit().putBoolean("unlicensed", true).apply()
        val req = PeriodicWorkRequestBuilder<Worker>(EVERY_HOURS, TimeUnit.HOURS)
            .setInitialDelay(EVERY_HOURS, TimeUnit.HOURS)
            .build()
        // KEEP: opening the purchase screen again does not restart the countdown.
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun onLicensed(ctx: Context) {
        prefs(ctx).edit().putBoolean("unlicensed", false).putInt("remind_count", 0).apply()
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
        ctx.getSystemService(android.app.NotificationManager::class.java).cancel(ID)
    }

    class Worker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val ctx = applicationContext
            val p = prefs(ctx)
            if (!p.getBoolean("unlicensed", false)) return Result.success()
            val sent = p.getInt("remind_count", 0)
            if (sent >= MAX_REMINDERS) {
                WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
                return Result.success()
            }
            if (!Notify.canPost(ctx)) return Result.success()
            val l = MainActivity.wrapLocale(ctx)
            Notify.post(
                ctx, l, ID, Notify.CH_PROMO,
                title = l.getString(R.string.promo_title),
                short = l.getString(R.string.promo_short),
                big = l.getString(R.string.promo_big),
                tap = Notify.openAppIntent(ctx),
                actionLabel = l.getString(R.string.lic_buy)
            )
            p.edit().putInt("remind_count", sent + 1).apply()
            return Result.success()
        }
    }
}
