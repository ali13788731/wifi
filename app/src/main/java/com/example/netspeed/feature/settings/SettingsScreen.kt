package com.example.netspeed.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.netspeed.core.ui.SoftCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onLanguageChanged: (String) -> Unit) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.settings_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Section(Icons.Rounded.CloudDownload, R.string.s_downloads) {
            Text(stringResource(R.string.s_max_concurrent) + ": ${s.maxConcurrent}", fontSize = 14.sp)
            Slider(s.maxConcurrent.toFloat(), { v -> vm.set { setMaxConcurrent(v.toInt()) } }, valueRange = 1f..5f, steps = 3)
            Text(stringResource(R.string.s_connections) + ": ${s.connectionsPerNetwork}", fontSize = 14.sp)
            Text(stringResource(R.string.s_connections_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(s.connectionsPerNetwork.toFloat(), { v -> vm.set { setConnections(v.toInt()) } }, valueRange = 1f..8f, steps = 6)
            NumberField(R.string.s_speed_limit, s.speedLimitBps / (1024 * 1024)) { vm.set { setSpeedLimit(it * 1024 * 1024) } }
            SwitchRow(R.string.s_auto_retry, s.autoRetry) { vm.set { setAutoRetry(it) } }
        }

        Section(Icons.Rounded.SignalCellularAlt, R.string.s_mobile_section) {
            Text(stringResource(R.string.s_mobile_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            NumberField(R.string.s_mobile_limit, s.mobileLimitBytes / (1024 * 1024)) { vm.set { setMobileLimit(it * 1024 * 1024) } }
            Text(stringResource(R.string.s_mobile_used) + ": " + Format.bytes(mobileUsed.value), fontSize = 13.sp)
            if (s.mobileLimitBytes > 0) LinearProgressIndicator(
                progress = { (mobileUsed.value.toFloat() / s.mobileLimitBytes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)
            )
        }

        Section(Icons.Rounded.Notifications, R.string.s_notifications) {
            SwitchRow(R.string.s_statusbar, s.statusBarSpeed) { vm.set { setStatusBarSpeed(it) } }
            Text(stringResource(R.string.s_statusbar_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section(Icons.Rounded.Link, R.string.s_links) {
            SwitchRow(R.string.s_clipboard, s.clipboardDetect) { vm.set { setClipboard(it) } }
            Text(stringResource(R.string.s_clipboard_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section(Icons.Rounded.Folder, R.string.s_storage) {
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

        Section(Icons.Rounded.Language, R.string.s_appearance) {
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

        Section(Icons.Rounded.Info, R.string.s_about) {
            Text(stringResource(R.string.app_name) + "  v" + BuildConfig.VERSION_NAME, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.designed_by), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Section(icon: ImageVector, title: Int, content: @Composable () -> Unit) {
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            content()
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
