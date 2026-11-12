package com.example.netspeed.feature.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.BuildConfig
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.Brand
import com.example.netspeed.core.ui.IconTile
import com.example.netspeed.core.ui.SoftCard

private class Entry(val key: String, val icon: ImageVector, val title: Int, val sub: Int, val tint: Color)

private val entries = listOf(
    Entry("downloads", Icons.Rounded.CloudDownload, R.string.s_downloads, R.string.sub_downloads, Color(0xFF3B6DFF)),
    Entry("mobile", Icons.Rounded.SignalCellularAlt, R.string.s_mobile_section, R.string.sub_mobile, Color(0xFFF07A1E)),
    Entry("notifications", Icons.Rounded.Notifications, R.string.s_notifications, R.string.sub_notifications, Color(0xFFE5484D)),
    Entry("links", Icons.Rounded.Link, R.string.s_links, R.string.sub_links, Color(0xFF14A46B)),
    Entry("storage", Icons.Rounded.Folder, R.string.s_storage, R.string.sub_storage, Color(0xFFE5A100)),
    Entry("appearance", Icons.Rounded.Palette, R.string.s_appearance, R.string.sub_appearance, Color(0xFF7C4DFF)),
    Entry("about", Icons.Rounded.Info, R.string.s_about, R.string.sub_about, Color(0xFF22A7D8))
)

/** Settings as a menu: every group is its own page, opened from a list. Back returns to the list. */
@Composable
fun SettingsScreen(vm: AppViewModel, onLanguageChanged: (String) -> Unit) {
    var page by rememberSaveable { mutableStateOf("") }
    BackHandler(page.isNotEmpty()) { page = "" }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState.isNotEmpty())
                (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 10 }) togetherWith fadeOut(tween(120))
            else
                (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { -it / 10 }) togetherWith fadeOut(tween(120))
        },
        label = "settings"
    ) { p ->
        if (p.isEmpty()) Menu(onOpen = { page = it })
        else Detail(vm, p, onBack = { page = "" }, onLanguageChanged = onLanguageChanged)
    }
}

@Composable
private fun Menu(onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.settings_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brand.gradientWide).padding(20.dp)
        ) {
            Text(stringResource(R.string.app_name), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("v" + BuildConfig.VERSION_NAME, color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.splash_tagline), color = Color.White.copy(alpha = .9f), fontSize = 13.sp)
        }
        entries.forEach { e ->
            SoftCard {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(e.key) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(e.icon, e.tint, 46.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(e.title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(stringResource(e.sub), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Detail(vm: AppViewModel, key: String, onBack: () -> Unit, onLanguageChanged: (String) -> Unit) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val entry = entries.first { it.key == key }
    val mobileUsed = remember { mutableStateOf(vm.manager.mobileUsedThisMonth()) }
    LaunchedEffect(Unit) {
        vm.manager.refreshMobileUsed()
        mobileUsed.value = vm.manager.mobileUsedThisMonth()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            vm.set { setTree(uri.toString()) }
        }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 8.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            IconTile(entry.icon, entry.tint, 36.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(entry.title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (key) {
                        "downloads" -> {
                            Text(stringResource(R.string.s_max_concurrent) + ": ${s.maxConcurrent}", fontSize = 14.sp)
                            Slider(s.maxConcurrent.toFloat(), { v -> vm.set { setMaxConcurrent(v.toInt()) } }, valueRange = 1f..5f, steps = 3)
                            Text(stringResource(R.string.s_connections) + ": ${s.connectionsPerNetwork}", fontSize = 14.sp)
                            Text(stringResource(R.string.s_connections_hint), fontSize = 12.sp, color = muted)
                            Slider(s.connectionsPerNetwork.toFloat(), { v -> vm.set { setConnections(v.toInt()) } }, valueRange = 1f..8f, steps = 6)
                            NumberField(R.string.s_speed_limit, s.speedLimitBps / (1024 * 1024)) { vm.set { setSpeedLimit(it * 1024 * 1024) } }
                            SwitchRow(R.string.s_auto_retry, s.autoRetry) { vm.set { setAutoRetry(it) } }
                        }
                        "mobile" -> {
                            Text(stringResource(R.string.s_mobile_hint), fontSize = 12.sp, color = muted)
                            NumberField(R.string.s_mobile_limit, s.mobileLimitBytes / (1024 * 1024)) { vm.set { setMobileLimit(it * 1024 * 1024) } }
                            Text(stringResource(R.string.s_mobile_used) + ": " + Format.bytes(mobileUsed.value), fontSize = 13.sp)
                            if (s.mobileLimitBytes > 0) LinearProgressIndicator(
                                progress = { (mobileUsed.value.toFloat() / s.mobileLimitBytes).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)
                            )
                        }
                        "notifications" -> {
                            SwitchRow(R.string.s_statusbar, s.statusBarSpeed) { vm.set { setStatusBarSpeed(it) } }
                            Text(stringResource(R.string.s_statusbar_hint), fontSize = 12.sp, color = muted)
                        }
                        "links" -> {
                            SwitchRow(R.string.s_clipboard, s.clipboardDetect) { vm.set { setClipboard(it) } }
                            Text(stringResource(R.string.s_clipboard_hint), fontSize = 12.sp, color = muted)
                        }
                        "storage" -> {
                            Text(
                                stringResource(R.string.s_folder) + ": " + (s.saveTree?.let { android.net.Uri.parse(it).lastPathSegment }
                                    ?: stringResource(R.string.s_folder_default)), fontSize = 13.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({ picker.launch(null) }) { Text(stringResource(R.string.s_choose_folder)) }
                                OutlinedButton({ vm.set { setTree(null) } }) { Text(stringResource(R.string.s_reset_folder)) }
                            }
                            SwitchRow(R.string.s_overwrite, s.overwrite) { vm.set { setOverwrite(it) } }
                        }
                        "appearance" -> {
                            Text(stringResource(R.string.s_theme), fontWeight = FontWeight.Medium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEach { (k, l) ->
                                    FilterChip(s.theme == k, { vm.set { setTheme(k) } }, { Text(stringResource(l)) })
                                }
                            }
                            Text(stringResource(R.string.s_language), fontWeight = FontWeight.Medium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("system" to R.string.lang_system, "fa" to R.string.lang_fa, "en" to R.string.lang_en).forEach { (k, l) ->
                                    FilterChip(s.language == k, { vm.set { setLanguage(k) }; onLanguageChanged(k) }, { Text(stringResource(l)) })
                                }
                            }
                        }
                        else -> {
                            Text(stringResource(R.string.app_name) + "  v" + BuildConfig.VERSION_NAME, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.designed_by), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: Int, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f), fontSize = 14.sp)
        Switch(value, onChange)
    }
}

@Composable
private fun NumberField(label: Int, value: Long, onChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text, { t -> text = t.filter { it.isDigit() }.take(7); onChange(text.toLongOrNull() ?: 0L) },
        Modifier.fillMaxWidth(), label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.medium
    )
}
