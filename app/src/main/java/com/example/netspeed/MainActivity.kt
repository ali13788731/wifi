package com.example.netspeed

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.DualNetTheme
import com.example.netspeed.feature.diagnostics.DiagnosticsScreen
import com.example.netspeed.feature.downloads.AddDownloadSheet
import com.example.netspeed.feature.downloads.DownloadsScreen
import com.example.netspeed.feature.history.HistoryScreen
import com.example.netspeed.feature.home.HomeScreen
import com.example.netspeed.feature.settings.SettingsScreen
import com.example.netspeed.service.DownloadService
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var incomingUrl by mutableStateOf<String?>(null)

    override fun attachBaseContext(base: Context) = super.attachBaseContext(wrapLocale(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val settings by vm.settings.collectAsState()
            DualNetTheme(settings.theme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A link shared/opened from another app only pre-fills the sheet; the user must confirm. */
    private fun handleIntent(i: Intent?) {
        incomingUrl = when (i?.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            Intent.ACTION_VIEW -> i.dataString
            else -> null
        }?.takeIf { it.startsWith("http", true) }
    }

    private fun clipboardUrl(): String? {
        if (!vm.settings.value.clipboardDetect) return null
        val cm = getSystemService(ClipboardManager::class.java)
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
        return t?.takeIf { (it.startsWith("http://", true) || it.startsWith("https://", true)) && !it.contains(' ') }
    }

    private fun startSpeedService() {
        val ok = Build.VERSION.SDK_INT < 33 ||
            PermissionChecker.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PermissionChecker.PERMISSION_GRANTED
        if (ok && vm.settings.value.statusBarSpeed) {
            ContextCompat.startForegroundService(this, Intent(this, DownloadService::class.java))
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppRoot() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showAdd by rememberSaveable { mutableStateOf(false) }
        var prefill by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }

        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            startSpeedService()
        }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startSpeedService()
            vm.manager.restore()
        }
        LaunchedEffect(incomingUrl) { if (incomingUrl != null) { prefill = incomingUrl; showAdd = true; incomingUrl = null } }

        val openAdd = { prefill = clipboardUrl(); showAdd = true }
        val open: (String) -> Unit = { p ->
            runCatching { startActivity(vm.storage.openIntent(p)) }
        }
        val share: (String) -> Unit = { p -> runCatching { startActivity(vm.storage.shareIntent(p)) } }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    listOf(
                        Icons.Rounded.Home to R.string.nav_home, Icons.Rounded.Download to R.string.nav_downloads,
                        Icons.Rounded.NetworkCheck to R.string.nav_diagnostics, Icons.Rounded.History to R.string.nav_history,
                        Icons.Rounded.Settings to R.string.nav_settings
                    ).forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icon, null) }, label = { Text(stringResource(label), maxLines = 1) }
                        )
                    }
                }
            }
        ) { pad ->
            androidx.compose.foundation.layout.Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> HomeScreen(vm, openAdd, open, share)
                    1 -> DownloadsScreen(vm, open, share)
                    2 -> DiagnosticsScreen(vm)
                    3 -> HistoryScreen(vm, open, share)
                    else -> SettingsScreen(vm) { lang ->
                        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("lang", lang).apply()
                        recreate()
                    }
                }
            }
        }
        if (showAdd) AddDownloadSheet(vm, prefill) { showAdd = false }
    }

    companion object {
        /** Applies the in-app language choice (fa/en/system) before any resource is read. */
        fun wrapLocale(base: Context): Context {
            val lang = base.getSharedPreferences("ui", MODE_PRIVATE).getString("lang", "system") ?: "system"
            if (lang == "system") return base
            val cfg = Configuration(base.resources.configuration)
            val loc = Locale(lang)
            Locale.setDefault(loc)
            cfg.setLocale(loc)
            cfg.setLayoutDirection(loc)
            return base.createConfigurationContext(cfg)
        }
    }
}
