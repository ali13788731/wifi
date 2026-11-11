package com.example.netspeed

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var toggle: Switch
    private lateinit var retestBtn: Button
    private lateinit var settingsBtn: Button

    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = mutableListOf<ConnectivityManager.NetworkCallback>()
    private val prefs by lazy { getSharedPreferences("netspeed", MODE_PRIVATE) }

    private var testing = false

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) tryEnable() else {
            setSwitch(false)
            showStatus("برای نمایش نوتیفیکیشن باید اجازه‌ی اعلان بدهید.", Color.parseColor("#C62828"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        toggle = Switch(this).apply {
            text = "نمایش سرعت در نوتیفیکیشن"
            textSize = 18f
            setOnClickListener { onToggleClicked() }
        }
        retestBtn = Button(this).apply {
            text = "تست مجدد"
            setOnClickListener { runSupportTest() }
        }
        settingsBtn = Button(this).apply {
            text = "باز کردن تنظیمات وای‌فای / دیتای موبایل"
            setOnClickListener { openNetworkSettings() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(status)
            addView(toggle)
            addView(retestBtn)
            addView(settingsBtn)
        }
        setContentView(root)
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        // تست پشتیبانی فقط تا وقتی که یک بار موفق نشده انجام می‌شود
        if (!isSupported() && !testing) runSupportTest()
    }

    private fun isSupported() = prefs.getBoolean("supported", false)
    private fun isRunning() = prefs.getBoolean("running", false)

    // ---------- UI ----------

    private fun showStatus(text: String, color: Int = Color.DKGRAY) {
        status.text = text
        status.setTextColor(color)
    }

    private fun setSwitch(on: Boolean) {
        toggle.isChecked = on
        prefs.edit().putBoolean("running", on).apply()
    }

    private fun refreshUi() {
        val supported = isSupported()
        toggle.isEnabled = supported && !testing
        toggle.isChecked = supported && isRunning()
        retestBtn.visibility = if (supported) View.GONE else View.VISIBLE
        retestBtn.isEnabled = !testing
        settingsBtn.visibility = if (supported) View.GONE else View.VISIBLE

        if (testing) return
        if (supported) {
            if (isRunning()) showStatus("✅ فعال است. سرعت در نوتیفیکیشن نمایش داده می‌شود.", Color.parseColor("#2E7D32"))
            else showStatus("✅ گوشی شما پشتیبانی می‌کند.\nبرای شروع، سوییچ را روشن کنید.", Color.parseColor("#2E7D32"))
        } else {
            showStatus(
                "❌ هنوز پشتیبانی تأیید نشده.\n\n" +
                    "برای تست، هم‌زمان باید وای‌فای وصل و دیتای موبایل روشن باشد. " +
                    "تا وقتی تأیید نشود، نمی‌توانید برنامه را روشن کنید.",
                Color.parseColor("#C62828")
            )
        }
    }

    private fun openNetworkSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 29)
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        else
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        startActivity(intent)
    }

    // ---------- تست پشتیبانی (فقط یک بار) ----------

    private fun runSupportTest() {
        testing = true
        showStatus("در حال بررسی پشتیبانی...")
        refreshUi()
        checkBothUp(4000) { ok ->
            testing = false
            if (ok) prefs.edit().putBoolean("supported", true).apply()
            refreshUi()
        }
    }

    // ---------- روشن / خاموش ----------

    private fun onToggleClicked() {
        if (toggle.isChecked) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                tryEnable()
            }
        } else {
            stopService(Intent(this, SpeedService::class.java))
            setSwitch(false)
            refreshUi()
        }
    }

    private fun tryEnable() {
        testing = true
        toggle.isEnabled = false
        showStatus("در حال بررسی وای‌فای و دیتای موبایل...")
        checkBothUp(2500) { ok ->
            testing = false
            if (ok) {
                ContextCompat.startForegroundService(this, Intent(this, SpeedService::class.java))
                setSwitch(true)
                refreshUi()
            } else {
                setSwitch(false)
                refreshUi()
                settingsBtn.visibility = View.VISIBLE
                showStatus(
                    "روشن نشد: وای‌فای و دیتای موبایل باید هر دو روشن باشند.\n\n" +
                        "اندروید اجازه نمی‌دهد برنامه‌ها خودشان آن‌ها را روشن کنند؛ " +
                        "از دکمه‌ی زیر آن‌ها را روشن کنید و دوباره امتحان کنید.",
                    Color.parseColor("#C62828")
                )
            }
        }
    }

    // ---------- بررسی هم‌زمان بودن وای‌فای و موبایل ----------

    private fun checkBothUp(timeoutMs: Long, onResult: (Boolean) -> Unit) {
        val cm = getSystemService(ConnectivityManager::class.java)
        var wifiUp = false
        var cellUp = false

        val wifiCb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { wifiUp = true }
        }
        val cellCb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { cellUp = true }
        }

        fun request(transport: Int, cb: ConnectivityManager.NetworkCallback) {
            val req = NetworkRequest.Builder()
                .addTransportType(transport)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            try {
                cm.requestNetwork(req, cb)
                callbacks.add(cb)
            } catch (_: Exception) {
            }
        }

        request(NetworkCapabilities.TRANSPORT_WIFI, wifiCb)
        request(NetworkCapabilities.TRANSPORT_CELLULAR, cellCb)

        handler.postDelayed({
            release(wifiCb)
            release(cellCb)
            onResult(wifiUp && cellUp)
        }, timeoutMs)
    }

    private fun release(cb: ConnectivityManager.NetworkCallback) {
        try {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb)
        } catch (_: Exception) {
        }
        callbacks.remove(cb)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        callbacks.toList().forEach { release(it) }
        super.onDestroy()
    }
}
