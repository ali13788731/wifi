package com.example.netspeed.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.netspeed.MainActivity
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.common.SettingsRepository
import com.example.netspeed.core.downloader.DownloadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One foreground service for two jobs:
 *  - keeps downloads alive in the background (screen off, activity destroyed)
 *  - shows live speed in the notification AND as a number in the status-bar icon.
 * While downloads run, the per-network speeds are the engine's real measured byte rates.
 * With no downloads it falls back to the device-wide TrafficStats counters.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var manager: DownloadManager
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var looping = false
    private var idleTicks = 0
    private var lastTotal = 0L
    private var lastMobile = 0L
    private var lastTime = 0L

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SPEED, getString(R.string.ch_speed), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, getString(R.string.ch_done), NotificationManager.IMPORTANCE_DEFAULT))
        val n = build(0, 0, 0, null)
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, n)
        lastTotal = TrafficStats.getTotalRxBytes(); lastMobile = TrafficStats.getMobileRxBytes()
        lastTime = SystemClock.elapsedRealtime()
        scope.launch { manager.events.collect { ev -> notifyDone(ev.id, ev.name, ev.ok, ev.message) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> manager.pauseAll()
            ACTION_RESUME_ALL -> manager.resumeAll()
            ACTION_CANCEL_ALL -> manager.live.value.keys.forEach { manager.cancel(it) }
        }
        manager.restore()
        if (!looping) { looping = true; scope.launch { loop() } }
        return START_STICKY
    }

    private suspend fun loop() {
        val nm = getSystemService(NotificationManager::class.java)
        while (true) {
            delay(1000)
            val live = manager.live.value.values.filter { it.status.name == "RUNNING" || it.status.name == "PROBING" }
            val showIcon = settings.flow.first().statusBarSpeed
            val wifi: Long; val mobile: Long
            if (live.isNotEmpty()) {
                wifi = live.sumOf { it.wifi.speedBps }; mobile = live.sumOf { it.mobile.speedBps }
            } else {
                val now = SystemClock.elapsedRealtime()
                val total = TrafficStats.getTotalRxBytes(); val mob = TrafficStats.getMobileRxBytes()
                val dt = (now - lastTime) / 1000.0
                if (dt > 0) {
                    val m = ((mob - lastMobile).coerceAtLeast(0) / dt).toLong()
                    val t = ((total - lastTotal).coerceAtLeast(0) / dt).toLong()
                    mobile = m; wifi = (t - m).coerceAtLeast(0)
                } else { mobile = 0; wifi = 0 }
                lastTotal = total; lastMobile = mob; lastTime = now
            }
            val pct: Int? = if (live.isNotEmpty()) {
                val t = live.sumOf { it.totalBytes.coerceAtLeast(0) }
                if (t > 0) (live.sumOf { it.doneBytes } * 100 / t).toInt() else -1
            } else null
            nm.notify(ID, build(wifi, mobile, live.size, pct, showIcon))

            if (live.isEmpty() && !showIcon) { if (++idleTicks > 5) { stopSelf(); return } } else idleTicks = 0
        }
    }

    private fun pi(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, DownloadService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun build(wifi: Long, mobile: Long, active: Int, pct: Int?, icon: Boolean = true): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val sum = wifi + mobile
        val b = NotificationCompat.Builder(this, CH_SPEED)
            .setSmallIcon(if (icon) IconCompat.createWithBitmap(textIcon(sum)) else IconCompat.createWithResource(this, R.drawable.ic_stat_net))
            .setContentTitle(getString(R.string.notif_total, Format.speed(sum)))
            .setContentText(getString(R.string.notif_split, Format.speed(wifi), Format.speed(mobile)))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
        if (active > 0) {
            b.setSubText(getString(R.string.notif_active, active))
            if (pct != null && pct >= 0) b.setProgress(100, pct, false) else b.setProgress(0, 0, true)
            b.addAction(0, getString(R.string.pause_all), pi(ACTION_PAUSE_ALL, 1))
            b.addAction(0, getString(R.string.cancel_all), pi(ACTION_CANCEL_ALL, 2))
        } else {
            b.addAction(0, getString(R.string.resume_all), pi(ACTION_RESUME_ALL, 3))
        }
        return b.build()
    }

    private fun notifyDone(id: Long, name: String, ok: Boolean, msg: String?) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CH_DONE)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(getString(if (ok) R.string.notif_done else R.string.notif_failed))
            .setContentText(if (ok) name else "$name — ${msg ?: ""}")
            .setAutoCancel(true).setContentIntent(open).build()
        getSystemService(NotificationManager::class.java).notify(1000 + id.toInt(), n)
    }

    /** Draws the total speed (MB/s) as the status-bar icon. */
    private fun textIcon(bps: Long): Bitmap {
        val mb = bps / (1024.0 * 1024.0)
        val text = if (mb >= 10) "%.0f".format(mb) else "%.1f".format(mb)
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
            textSize = if (text.length >= 3) 64f else 80f
        }
        Canvas(bmp).drawText(text, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2f, p)
        return bmp
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CH_SPEED = "speed"
        const val CH_DONE = "done"
        const val ID = 1
        const val ACTION_PAUSE_ALL = "dualnet.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "dualnet.RESUME_ALL"
        const val ACTION_CANCEL_ALL = "dualnet.CANCEL_ALL"
    }
}
