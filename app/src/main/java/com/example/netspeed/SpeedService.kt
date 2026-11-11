package com.example.netspeed

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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat

class SpeedService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastTotal = 0L
    private var lastMobile = 0L
    private var lastTime = 0L

    private val ticker = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Network speed", NotificationManager.IMPORTANCE_LOW)
        )
        lastTotal = TrafficStats.getTotalRxBytes()
        lastMobile = TrafficStats.getMobileRxBytes()
        lastTime = SystemClock.elapsedRealtime()

        val n = build(0.0, 0.0, 0.0)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }
        handler.postDelayed(ticker, 1000)
    }

    private fun update() {
        val now = SystemClock.elapsedRealtime()
        val total = TrafficStats.getTotalRxBytes()
        val mobile = TrafficStats.getMobileRxBytes()
        val dt = (now - lastTime) / 1000.0
        if (dt <= 0) return

        val mobileSpeed = ((mobile - lastMobile).coerceAtLeast(0)) / dt / MB
        val totalSpeed = ((total - lastTotal).coerceAtLeast(0)) / dt / MB
        // وای‌فای = کل ترافیک - ترافیک موبایل
        val wifiSpeed = (totalSpeed - mobileSpeed).coerceAtLeast(0.0)

        lastTotal = total
        lastMobile = mobile
        lastTime = now

        getSystemService(NotificationManager::class.java)
            .notify(ID, build(wifiSpeed, mobileSpeed, wifiSpeed + mobileSpeed))
    }

    private fun build(wifi: Double, mobile: Double, sum: Double): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(IconCompat.createWithBitmap(textIcon(sum)))
            .setContentTitle("Total: %.2f MB/s".format(sum))
            .setContentText("WiFi: %.2f MB/s  |  Mobile: %.2f MB/s".format(wifi, mobile))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "WiFi: %.2f MB/s\nMobile: %.2f MB/s\nTotal: %.2f MB/s"
                        .format(wifi, mobile, sum)
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .build()
    }

    /** عدد مجموع سرعت رو داخل آیکون نوار وضعیت (status bar) می‌کشه */
    private fun textIcon(value: Double): Bitmap {
        val text = if (value >= 10) "%.0f".format(value) else "%.1f".format(value)
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = if (text.length >= 3) 56f else 72f
        }
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(text, size / 2f, y, p)
        return bmp
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "speed"
        private const val ID = 1
        private const val MB = 1024.0 * 1024.0
    }
}
