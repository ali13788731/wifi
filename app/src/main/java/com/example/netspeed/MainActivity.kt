package com.example.netspeed

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.DualNetTheme
import com.example.netspeed.core.ui.Splash
import com.example.netspeed.feature.diagnostics.DiagnosticsScreen
import com.example.netspeed.feature.downloads.AddDownloadSheet
import com.example.netspeed.feature.downloads.DownloadsScreen
import com.example.netspeed.feature.history.HistoryScreen
import com.example.netspeed.feature.history.StatsScreen
import com.example.netspeed.feature.home.HomeScreen
import com.example.netspeed.feature.settings.SettingsScreen
import com.example.netspeed.service.DownloadService
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/** A link waiting to be shown in the "new download" sheet. [auto] = check it immediately. */
private data class Incoming(val url: String, val auto: Boolean)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var incoming by mutableStateOf<Incoming?>(null)

    override fun attachBaseContext(base: Context) = super.attachBaseContext(wrapLocale(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val settings by vm.settings.collectAsState()
            val systemDir = LocalLayoutDirection.current
            // Persian = right-to-left everywhere, including the menu.
            val dir = when (settings.language) {
                "fa" -> LayoutDirection.Rtl
                "en" -> LayoutDirection.Ltr
                else -> systemDir
            }
            CompositionLocalProvider(LocalLayoutDirection provides dir) {
                DualNetTheme(settings.theme) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        var splash by remember { mutableStateOf(!splashShown) }
                        if (splash) Splash { splashShown = true; splash = false } else AppRoot()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A link opened/shared from another app is checked at once, but only started after the user confirms. */
    private fun handleIntent(i: Intent?) {
        val url = when (i?.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.let { Regex("""https?://\S+""").find(it)?.value }
            Intent.ACTION_VIEW -> i.dataString
            else -> null
        }?.trim()
        if (url != null && url.startsWith("http", true)) incoming = Incoming(url, true)
    }

    /**
     * Android only lets the focused app read the clipboard, so this runs when the app comes to the
     * front: a link copied since last time opens the new-download sheet automatically.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !vm.settings.value.clipboardDetect || !splashShown) return
        val cm = getSystemService(ClipboardManager::class.java)
        val t = runCatching {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
        }.getOrNull() ?: return
        val link = Regex("""https?://\S+""").find(t)?.value ?: return
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (prefs.getString("lastClip", null) == link) return
        prefs.edit().putString("lastClip", link).apply()
        incoming = Incoming(link, true)
    }

    private fun startSpeedService() {
        val ok = Build.VERSION.SDK_INT < 33 ||
            PermissionChecker.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PermissionChecker.PERMISSION_GRANTED
        if (ok && vm.settings.value.statusBarSpeed) {
            ContextCompat.startForegroundService(this, Intent(this, DownloadService::class.java))
        }
    }

    @Composable
    private fun AppRoot() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showAdd by rememberSaveable { mutableStateOf(false) }
        var showDiag by rememberSaveable { mutableStateOf(false) }
        var prefill by remember { mutableStateOf<Incoming?>(null) }

        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startSpeedService() }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else startSpeedService()
            vm.manager.restore()
        }
        LaunchedEffect(incoming) {
            incoming?.let { prefill = it; showAdd = true; incoming = null }
        }
        BackHandler(showDiag) { showDiag = false }

        val open: (String) -> Unit = { p -> runCatching { startActivity(vm.storage.openIntent(p)) } }
        val share: (String) -> Unit = { p -> runCatching { startActivity(vm.storage.shareIntent(p)) } }

        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxSize().padding(bottom = 84.dp)) {
                if (showDiag) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ showDiag = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
                    }
                    Box(Modifier.padding(top = 56.dp)) { DiagnosticsScreen(vm) }
                } else when (tab) {
                    0 -> HomeScreen(vm, { prefill = null; showAdd = true }, open, share, { showDiag = true })
                    1 -> DownloadsScreen(vm, open, share)
                    2 -> HistoryScreen(vm, open, share)
                    3 -> StatsScreen(vm)
                    else -> SettingsScreen(vm) { lang ->
                        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("lang", lang).apply()
                        recreate()
                    }
                }
            }
            NavPill(
                tab, { tab = it; showDiag = false },
                listOf(
                    Icons.Rounded.Home to R.string.nav_home, Icons.Rounded.Download to R.string.nav_downloads,
                    Icons.Rounded.History to R.string.nav_history, Icons.Rounded.BarChart to R.string.nav_stats,
                    Icons.Rounded.Settings to R.string.nav_settings
                ),
                Modifier.align(Alignment.BottomCenter)
            )
        }
        if (showAdd) AddDownloadSheet(vm, prefill?.url, prefill?.auto == true) { showAdd = false }
    }

    companion object {
        private var splashShown = false

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

/** Floating rounded navigation bar: the selected tab expands into a pill with its label. */
@Composable
private fun NavPill(selected: Int, onSelect: (Int) -> Unit, items: List<Pair<ImageVector, Int>>, modifier: Modifier) {
    Surface(
        modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
        shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp, shadowElevation = 10.dp
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, (icon, label) ->
                val on = i == selected
                Row(
                    Modifier.clip(RoundedCornerShape(24.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = if (on) 14.dp else 12.dp, vertical = 10.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, stringResource(label),
                        tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (on) {
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(label), color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}
