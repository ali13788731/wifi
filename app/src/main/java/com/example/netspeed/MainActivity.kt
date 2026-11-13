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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import com.example.netspeed.core.common.LinkDetector
import com.example.netspeed.core.license.LicenseManager
import com.example.netspeed.core.notify.Notify
import com.example.netspeed.core.notify.UpdateChecker
import kotlinx.coroutines.launch
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.Brand
import com.example.netspeed.core.ui.DualNetTheme
import com.example.netspeed.core.ui.LocalLicense
import com.example.netspeed.core.ui.ProDialog
import com.example.netspeed.core.ui.ProLockedScreen
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
    private val license by lazy { LicenseManager(this, vm.pro) }
    private var incoming by mutableStateOf<Incoming?>(null)

    override fun attachBaseContext(base: Context) = super.attachBaseContext(wrapLocale(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A fresh start (savedInstanceState == null) means the app was closed and opened again.
        // Minimising and coming back never calls onCreate, and a recreate (language change) has a
        // saved state, so the splash is NOT shown for those.
        splashShowing = savedInstanceState == null
        license.check()
        UpdateChecker.checkNow(this)
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
            CompositionLocalProvider(LocalLayoutDirection provides dir, LocalLicense provides license) {
                DualNetTheme(settings.theme) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        var splash by rememberSaveable { mutableStateOf(splashShowing) }
                        if (splash) Splash {
                            splashShowing = false; splash = false
                            checkClipboard()          // focus event came while the splash was on: look now
                        } else AppRoot()      // the app is free: never blocked by a purchase screen
                    }
                }
            }
        }
    }

    override fun onDestroy() { license.release(); super.onDestroy() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A link opened/shared from another app is checked at once, but only started after the user confirms. */
    private fun handleIntent(i: Intent?) {
        val url = when (i?.action) {
            Intent.ACTION_SEND -> LinkDetector.extract(i.getStringExtra(Intent.EXTRA_TEXT))
            Intent.ACTION_VIEW -> i.dataString?.trim()
            else -> null
        }
        if (url != null && url.startsWith("http", true)) incoming = Incoming(url, true)
    }

    /**
     * Android only lets the focused app read the clipboard, so this runs when the app comes to the
     * front (and once more when the splash ends). A copied link opens the new-download window only
     * if it really is a downloadable FILE; ordinary web-page links are ignored.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboard()
    }

    private fun checkClipboard() {
        if (splashShowing || !vm.settings.value.clipboardDetect) return
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val link = runCatching {
            LinkDetector.extract(cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString())
        }.getOrNull() ?: return
        // Same link + same copy time = already handled. Copying it again gives a new timestamp.
        val stamp = runCatching { cm.primaryClipDescription?.timestamp ?: 0L }.getOrDefault(0L)
        val key = "$link|$stamp"
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (prefs.getString("lastClip", null) == key) return
        prefs.edit().putString("lastClip", key).apply()
        lifecycleScope.launch {
            if (vm.isDownloadable(link)) incoming = Incoming(link, true)
        }
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
        val isPro by vm.isPro.collectAsState()
        var proDialog by remember { mutableStateOf<Int?>(null) }

        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startSpeedService() }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else startSpeedService()
            vm.manager.restore()
        }
        LaunchedEffect(incoming) {
            incoming?.let { prefill = it; showAdd = true; incoming = null }
        }
        BackHandler(showDiag) { showDiag = false }
        // another screen asked to open a settings page (e.g. "download with one network")
        LaunchedEffect(vm.settingsRequest) {
            if (vm.settingsRequest != null) { tab = 4; showDiag = false; showAdd = false }
        }
        // room for the floating bar INCLUDING the system navigation bar, so the last row is never hidden under it
        val navInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        val open: (String) -> Unit = { p -> runCatching { startActivity(vm.storage.openIntent(p)) } }
        val share: (String) -> Unit = { p -> runCatching { startActivity(vm.storage.shareIntent(p)) } }

        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxSize().padding(bottom = 96.dp + navInset)) {
                AnimatedContent(
                    targetState = if (showDiag) -1 else tab,
                    transitionSpec = {
                        (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 24 }) togetherWith fadeOut(tween(140))
                    },
                    label = "page"
                ) { page ->
                    when (page) {
                        -1 -> Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton({ showDiag = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                            }
                            DiagnosticsScreen(vm)
                        }
                        0 -> HomeScreen(vm, { prefill = null; showAdd = true }, open, share, { showDiag = true })
                        1 -> DownloadsScreen(vm, open, share)
                        2 -> if (isPro) HistoryScreen(vm, open, share) else ProLockedScreen(R.string.pro_history_locked) { proDialog = R.string.pro_feat_history }
                        3 -> if (isPro) StatsScreen(vm) else ProLockedScreen(R.string.pro_stats_locked) { proDialog = R.string.pro_feat_stats }
                        else -> SettingsScreen(vm) { lang ->
                            getSharedPreferences("ui", MODE_PRIVATE).edit().putString("lang", lang).apply()
                            recreate()
                        }
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
        if (proDialog != null && !isPro) ProDialog(proDialog) { proDialog = null }
    }

    companion object {
        /** True only from a fresh launch until the splash finishes. Never stays true across restarts. */
        @Volatile private var splashShowing = false

        /** Applies the in-app language choice (fa/en/system) before any resource is read. */
        fun wrapLocale(base: Context): Context {
            val lang = base.getSharedPreferences("ui", MODE_PRIVATE).getString("lang", "system") ?: "system"
            if (lang == "system") {
                com.example.netspeed.core.common.Format.persian = Locale.getDefault().language == "fa"
                return base
            }
            com.example.netspeed.core.common.Format.persian = lang == "fa"
            val cfg = Configuration(base.resources.configuration)
            val loc = Locale(lang)
            Locale.setDefault(loc)
            cfg.setLocale(loc)
            cfg.setLayoutDirection(loc)
            return base.createConfigurationContext(cfg)
        }
    }
}

/** Floating rounded navigation bar: the selected tab becomes a gradient pill that shows its label. */
@Composable
private fun NavPill(selected: Int, onSelect: (Int) -> Unit, items: List<Pair<ImageVector, Int>>, modifier: Modifier) {
    Surface(
        modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
        shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp, shadowElevation = 14.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, (icon, label) ->
                val on = i == selected
                Row(
                    Modifier.clip(RoundedCornerShape(26.dp))
                        .then(if (on) Modifier.background(Brand.gradient) else Modifier)
                        .clickable { onSelect(i) }
                        .padding(horizontal = if (on) 16.dp else 12.dp, vertical = 11.dp)
                        .animateContentSize(tween(260)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, stringResource(label),
                        tint = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (on) {
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(label), color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}
