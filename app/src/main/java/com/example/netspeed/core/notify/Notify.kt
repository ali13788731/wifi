package com.example.netspeed.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.netspeed.MainActivity
import com.example.netspeed.R

/** Small helper shared by the purchase-reminder and the update-available notifications. */
object Notify {
    const val CH_PROMO = "promo"
    const val CH_UPDATE = "update"

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** [lctx] = context wrapped with the in-app language (see MainActivity.wrapLocale). */
    private fun ensureChannels(ctx: Context, lctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROMO, lctx.getString(R.string.ch_promo), NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATE, lctx.getString(R.string.ch_update), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Posts a big-text notification. Silently does nothing when the notification permission is missing. */
    fun post(
        ctx: Context, lctx: Context, id: Int, channel: String,
        title: String, short: String, big: String,
        tap: PendingIntent, actionLabel: String? = null
    ) {
        if (!canPost(ctx)) return
        ensureChannels(ctx, lctx)
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_net)
            .setContentTitle(title)
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setColor(0xFF0B5FFF.toInt())
            .setAutoCancel(true)
            .setContentIntent(tap)
        if (actionLabel != null) b.addAction(0, actionLabel, tap)
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(id, b.build()) }
    }
}
