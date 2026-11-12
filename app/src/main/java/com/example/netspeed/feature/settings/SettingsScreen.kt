package com.example.netspeed.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.ui.AppViewModel

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onLanguageChanged: (String) -> Unit) {
    val s by vm.settings.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val mobileUsed = remember { mutableStateOf(vm.manager.mobileUsedThisMonth()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
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
        Text(stringResource(R.string.settings_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Section(R.string.s_network)
        Text(stringResource(R.string.s_default_mode))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("DUAL" to R.string.mode_dual, "WIFI_ONLY" to R.string.mode_wifi,
                "MOBILE_ONLY" to R.string.mode_mobile, "AUTO" to R.string.mode_auto).forEach { (k, l) ->
                FilterChip(s.defaultMode == k, { vm.set { setMode(k) } }, { Text(stringResource(l)) })
            }
        }
        SwitchRow(R.string.s_mobile_warning, s.mobileWarning) { vm.set { setMobileWarning(it) } }
        NumberField(R.string.s_mobile_limit, s.mobileLimitBytes / (1024 * 1024)) { vm.set { setMobileLimit(it * 1024 * 1024) } }
        Text(stringResource(R.string.s_mobile_used) + ": " + Format.bytes(mobileUsed.value),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Section(R.string.s_downloads)
        Text(stringResource(R.string.s_max_concurrent) + ": ${s.maxConcurrent}")
        Slider(s.maxConcurrent.toFloat(), { v -> vm.set { setMaxConcurrent(v.toInt()) } }, valueRange = 1f..5f, steps = 3)
        Text(stringResource(R.string.s_connections) + ": ${s.connectionsPerNetwork}")
        Slider(s.connectionsPerNetwork.toFloat(), { v -> vm.set { setConnections(v.toInt()) } }, valueRange = 1f..8f, steps = 6)
        NumberField(R.string.s_speed_limit, s.speedLimitBps / (1024 * 1024)) { vm.set { setSpeedLimit(it * 1024 * 1024) } }
        SwitchRow(R.string.s_auto_retry, s.autoRetry) { vm.set { setAutoRetry(it) } }
        SwitchRow(R.string.s_clipboard, s.clipboardDetect) { vm.set { setClipboard(it) } }
        SwitchRow(R.string.s_statusbar, s.statusBarSpeed) { vm.set { setStatusBarSpeed(it) } }
        Text(stringResource(R.string.s_statusbar_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Section(R.string.s_storage)
        Text(stringResource(R.string.s_folder) + ": " + (s.saveTree?.let { android.net.Uri.parse(it).lastPathSegment }
            ?: stringResource(R.string.s_folder_default)), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ picker.launch(null) }) { Text(stringResource(R.string.s_choose_folder)) }
            OutlinedButton({ vm.set { setTree(null) } }) { Text(stringResource(R.string.s_reset_folder)) }
        }
        SwitchRow(R.string.s_overwrite, s.overwrite) { vm.set { setOverwrite(it) } }

        Section(R.string.s_appearance)
        Text(stringResource(R.string.s_theme))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEach { (k, l) ->
                FilterChip(s.theme == k, { vm.set { setTheme(k) } }, { Text(stringResource(l)) })
            }
        }
        Text(stringResource(R.string.s_language))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to R.string.lang_system, "fa" to R.string.lang_fa, "en" to R.string.lang_en).forEach { (k, l) ->
                FilterChip(s.language == k, { vm.set { setLanguage(k) }; onLanguageChanged(k) }, { Text(stringResource(l)) })
            }
        }
    }
}

@Composable
private fun Section(res: Int) =
    Text(stringResource(res), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun SwitchRow(label: Int, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun NumberField(label: Int, value: Long, onChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text, { t -> text = t.filter { it.isDigit() }.take(7); onChange(text.toLongOrNull() ?: 0L) },
        Modifier.fillMaxWidth(), label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
