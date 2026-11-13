package com.example.netspeed.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.netspeed.MainActivity
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.common.SettingsRepository
import com.example.netspeed.core.common.Texts
import com.example.netspeed.core.downloader.DownloadManager
import com.example.netspeed.core.downloader.DownloadStatus
import com.example.netspeed.core.downloader.LiveProgress
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
 *  - keeps downloads alive in the background (screen off, activity destroyed);
 *  - shows live speed in the status bar (a two-line icon: Wi-Fi on top, mobile below) and a
 *    rich notification with progress, remaining, ETA and the per-network speed and bytes.
 * While downloads run the numbers are the engine's real measured byte rates; with no download it
 * falls back to the device-wide TrafficStats counters.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var manager: DownloadManager
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** Context that follows the in-app language (fa/en), not only the phone's language. */
    private var lctx: Context = this
    /** Re-read the language every time: the user may switch it while the service is running. */
    private fun refreshLocale() { lctx = MainActivity.wrapLocale(this) }
    private var looping = false
    private var idleTicks = 0
    private var lastTotal = 0L
    private var lastMobile = 0L
    private var lastTime = 0L

    override fun onCreate() {
        super.onCreate()
        refreshLocale()
        val nm = getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("speed")       // old LOW channel: OEMs hide its status-bar icon
        // DEFAULT importance (but silent) so phones that hide "silent notification icons" still show it.
        nm.createNotificationChannel(NotificationChannel(CH_LIVE, lctx.getString(R.string.ch_speed), NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null); enableVibration(false); enableLights(false); setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(CH_DONE, lctx.getString(R.string.ch_done), NotificationManager.IMPORTANCE_DEFAULT))
        val n = idle(0, 0, true)
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, n)
        lastTotal = TrafficStats.getTotalRxBytes(); lastMobile = TrafficStats.getMobileRxBytes()
        lastTime = SystemClock.elapsedRealtime()
        scope.launch { manager.events.collect { ev -> notifyDone(ev.id, ev.name, ev.ok, ev.message, ev.wifiBytes, ev.mobileBytes) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshLocale()
        when (intent?.action) {
            ACTION_PAUSE_ALL -> manager.pauseAll()
            ACTION_RESUME_ALL -> {
                // continuing needs both networks: say which one is off instead of silently doing nothing
                val missing = manager.missingNetwork()
                if (missing != null) android.widget.Toast.makeText(lctx, Texts.blocked(lctx, missing), android.widget.Toast.LENGTH_LONG).show()
                else manager.resumeAll()
            }
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
            refreshLocale()
            val live = manager.live.value.values.filter {
                it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PROBING
            }
            val showIcon = settings.flow.first().statusBarSpeed
            if (live.isNotEmpty()) {
                nm.notify(ID, active(live, showIcon))
            } else {
                val now = SystemClock.elapsedRealtime()
                val total = TrafficStats.getTotalRxBytes(); val mob = TrafficStats.getMobileRxBytes()
                val dt = (now - lastTime) / 1000.0
                var m = 0L; var w = 0L
                if (dt > 0) {
                    m = ((mob - lastMobile).coerceAtLeast(0) / dt).toLong()
                    w = (((total - lastTotal).coerceAtLeast(0) / dt).toLong() - m).coerceAtLeast(0)
                }
                lastTotal = total; lastMobile = mob; lastTime = now
                nm.notify(ID, idle(w, m, showIcon))
            }
            if (live.isEmpty() && !showIcon) { if (++idleTicks > 5) { stopSelf(); return } } else idleTicks = 0
        }
    }

    /** Persian lines start with an RTL mark so every notification line is laid out right-to-left. */
    private fun rl(t: String) = if (Format.persian) "\u200F" + t else t

    private fun pi(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, DownloadService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE
    )

    private fun base(wifi: Long, mobile: Long, icon: Boolean) = NotificationCompat.Builder(this, CH_LIVE)
        .setSmallIcon(
            if (icon) IconCompat.createWithBitmap(speedIcon(wifi, mobile))
            else IconCompat.createWithResource(this, R.drawable.ic_stat_net)
        )
        .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setColor(0xFF0B5FFF.toInt())
        .setContentIntent(openApp())

    /** Notification while nothing is downloading: device-wide Wi-Fi / mobile speed. */
    private fun idle(wifi: Long, mobile: Long, icon: Boolean): Notification =
        base(wifi, mobile, icon)
            .setContentTitle(rl(lctx.getString(R.string.notif_idle)))
            .setContentText(rl(lctx.getString(R.string.notif_split, Format.speed(wifi), Format.speed(mobile))))
            .addAction(0, lctx.getString(R.string.resume_all), pi(ACTION_RESUME_ALL, 3))
            .build()

    /** Rich notification while downloading: progress bar with the numbers in the middle of it. */
    private fun active(live: List<LiveProgress>, icon: Boolean): Notification {
        val wifi = live.sumOf { it.wifi.speedBps }
        val mobile = live.sumOf { it.mobile.speedBps }
        val total = live.sumOf { it.totalBytes.coerceAtLeast(0) }
        val done = live.sumOf { it.doneBytes }
        val wBytes = live.sumOf { it.wifi.bytes }
        val mBytes = live.sumOf { it.mobile.bytes }
        val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
        val speed = wifi + mobile
        val eta = if (speed > 0) (total - done).coerceAtLeast(0) / speed else -1
        val title = if (live.size == 1) manager.nameOf(live[0].downloadId) ?: lctx.getString(R.string.app_name)
        else lctx.getString(R.string.notif_n_downloads, live.size)
        val notice = live.firstNotNullOfOrNull { Texts.notice(lctx, it.notice) }

        val pctText = Format.pct(pct) + "  •  " + Format.bytes(done) + " / " + Format.bytes(total)
        val wifiLine = "● " + lctx.getString(R.string.wifi) + ":  " + Format.speed(wifi) + "  •  " + Format.bytes(wBytes)
        val mobLine = "● " + lctx.getString(R.string.mobile) + ":  " + Format.speed(mobile) + "  •  " + Format.bytes(mBytes)

        // Persian gets its own right-to-left layouts (the system layout direction follows the phone, not the app)
        val rtl = Format.persian
        val small = RemoteViews(packageName, if (rtl) R.layout.notif_small_rtl else R.layout.notif_small).apply {
            setTextViewText(R.id.n_title, rl(title))
            setProgressBar(R.id.n_bar, 100, pct, total <= 0)
            setTextViewText(R.id.n_pct, pctText)
            setTextViewText(R.id.n_speeds, rl(notice
                ?: (lctx.getString(R.string.wifi) + " " + Format.speed(wifi) + "  |  " + lctx.getString(R.string.mobile) + " " + Format.speed(mobile))))
        }
        val big = RemoteViews(packageName, if (rtl) R.layout.notif_big_rtl else R.layout.notif_big).apply {
            setTextViewText(R.id.n_title, rl(title))
            setProgressBar(R.id.n_bar, 100, pct, total <= 0)
            setTextViewText(R.id.n_pct, pctText)
            setTextViewText(R.id.n_remain, rl(notice
                ?: (lctx.getString(R.string.remaining) + " " + Format.bytes((total - done).coerceAtLeast(0)) +
                    "  •  " + lctx.getString(R.string.eta) + " " + Format.eta(eta))))
            setTextViewText(R.id.n_wifi, rl(wifiLine))
            setTextViewText(R.id.n_mobile, rl(mobLine))
            setTextViewText(R.id.n_total, rl(lctx.getString(R.string.notif_total, Format.speed(speed))))
            setTextColor(R.id.n_wifi, 0xFF2F80FF.toInt())
            setTextColor(R.id.n_mobile, 0xFFFF8A3D.toInt())
        }
        return base(wifi, mobile, icon)
            .setContentTitle(title)
            .setContentText(rl(lctx.getString(R.string.notif_split, Format.speed(wifi), Format.speed(mobile))))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .addAction(0, lctx.getString(R.string.pause_all), pi(ACTION_PAUSE_ALL, 1))
            .addAction(0, lctx.getString(R.string.cancel_all), pi(ACTION_CANCEL_ALL, 2))
            .build()
    }

    private fun notifyDone(id: Long, name: String, ok: Boolean, code: String?, wifiB: Long, mobB: Long) {
        val text = if (ok) {
            val sum = (wifiB + mobB).coerceAtLeast(1)
            lctx.getString(R.string.notif_done_split, Format.bytes(wifiB), (wifiB * 100 / sum).toInt(),
                Format.bytes(mobB), (mobB * 100 / sum).toInt())
        } else Texts.error(lctx, code)
        val n = NotificationCompat.Builder(this, CH_DONE)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(rl(lctx.getString(if (ok) R.string.notif_done else R.string.notif_failed) + ": " + name))
            .setContentText(rl(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(rl(text)))
            .setColor(0xFF0B5FFF.toInt())
            .setAutoCancel(true).setContentIntent(openApp()).build()
        getSystemService(NotificationManager::class.java).notify(1000 + id.toInt(), n)
    }

    /**
     * Status-bar icon: square, two rows centred. Wi-Fi speed on top, mobile speed below,
     * with a thin horizontal divider between them. Square so the system doesn't squash it.
     */
    private fun speedIcon(wifi: Long, mobile: Long): Bitmap {
        val s = 96
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowH = s / 2f
        val maxW = s - 4f
        // Draws [text] centred horizontally in one row, condensed horizontally if too wide.
        fun row(text: String, top: Float) {
            p.textSize = 50f
            p.textScaleX = 1f
            val tw = p.measureText(text)
            if (tw > maxW) p.textScaleX = maxW / tw
            val fm = p.fontMetrics
            val base = top + rowH / 2f - (fm.ascent + fm.descent) / 2f
            c.drawText(text, s / 2f, base, p)
        }
        row(Format.iconSpeed(wifi), 0f)
        row(Format.iconSpeed(mobile), rowH)
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 160 }
        c.drawRoundRect(RectF(14f, rowH - 1.5f, s - 14f, rowH + 1.5f), 1.5f, 1.5f, bar)
        return bmp
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CH_LIVE = "live"
        const val CH_DONE = "done"
        const val ID = 1
        const val ACTION_PAUSE_ALL = "dualnet.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "dualnet.RESUME_ALL"
        const val ACTION_CANCEL_ALL = "dualnet.CANCEL_ALL"
    }
}
