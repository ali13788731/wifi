package com.example.netspeed.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.downloader.NetworkMode
import com.example.netspeed.core.downloader.RequestOptions
import com.example.netspeed.core.ui.AddState
import com.example.netspeed.core.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddDownloadSheet(vm: AppViewModel, initialUrl: String?, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val state = vm.addState
    var url by remember { mutableStateOf(initialUrl ?: "") }
    var ua by remember { mutableStateOf(RequestOptions.DEFAULT_UA) }
    var referer by remember { mutableStateOf("") }
    var cookies by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var mode by remember(settings.defaultMode) { mutableStateOf(NetworkMode.valueOf(settings.defaultMode)) }
    var delayMs by remember { mutableLongStateOf(0L) }
    var confirmMobile by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.resetAdd() }
    LaunchedEffect(state) { if (state is AddState.Ready) name = state.probe.fileName ?: "" }

    fun opts() = RequestOptions(
        userAgent = ua.ifBlank { RequestOptions.DEFAULT_UA },
        referer = referer.ifBlank { null }, cookies = cookies.ifBlank { null },
        username = user.ifBlank { null }, password = pass.ifBlank { null }
    )

    ModalBottomSheet(
        onDismissRequest = { vm.resetAdd(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.add_download), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                url, { url = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.url)) },
                singleLine = true, enabled = state !is AddState.Checking
            )
            TextButton({ advanced = !advanced }) { Text(stringResource(R.string.advanced)) }
            if (advanced) {
                OutlinedTextField(ua, { ua = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.user_agent)) })
                OutlinedTextField(referer, { referer = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.referer)) })
                OutlinedTextField(cookies, { cookies = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.cookies)) })
                OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.username)) })
                OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.password)) })
            }

            when (state) {
                is AddState.Checking -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.checking))
                }
                is AddState.Error -> Text(
                    if (state.message == "invalid") stringResource(R.string.invalid_url) else state.message,
                    color = MaterialTheme.colorScheme.error
                )
                is AddState.Ready -> ReadyPart(
                    state, name, { name = it }, mode, { mode = it }, delayMs, { delayMs = it }
                )
                AddState.Idle -> {}
            }

            if (state is AddState.Ready) {
                val usesMobile = mode != NetworkMode.WIFI_ONLY
                Button(
                    onClick = {
                        if (usesMobile && settings.mobileWarning) confirmMobile = true
                        else { vm.confirmAdd(state, mode, name, delayMs); onDismiss() }
                    },
                    Modifier.fillMaxWidth()
                ) { Text(stringResource(if (delayMs > 0) R.string.schedule else R.string.start_now)) }
            } else {
                Button(
                    onClick = { vm.inspect(url, opts()) },
                    Modifier.fillMaxWidth(), enabled = state !is AddState.Checking
                ) { Text(stringResource(R.string.check_link)) }
            }
            OutlinedButton({ vm.resetAdd(); onDismiss() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
        }
    }

    if (confirmMobile && state is AddState.Ready) {
        AlertDialog(
            onDismissRequest = { confirmMobile = false },
            title = { Text(stringResource(R.string.mobile_warning_title)) },
            text = { Text(stringResource(R.string.mobile_warning_body, Format.bytes(state.probe.totalBytes))) },
            confirmButton = {
                TextButton({ confirmMobile = false; vm.confirmAdd(state, mode, name, delayMs); onDismiss() }) {
                    Text(stringResource(R.string.continue_))
                }
            },
            dismissButton = { TextButton({ confirmMobile = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReadyPart(
    s: AddState.Ready, name: String, onName: (String) -> Unit,
    mode: NetworkMode, onMode: (NetworkMode) -> Unit, delayMs: Long, onDelay: (Long) -> Unit
) {
    OutlinedTextField(name, onName, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.file_name)) })
    Text(stringResource(R.string.file_size) + ": " + Format.bytes(s.probe.totalBytes), fontSize = 14.sp)
    Text(
        stringResource(if (s.probe.canMultiPart) R.string.range_ok else R.string.range_no) +
            if (!s.probe.canMultiPart) "\n" + s.probe.message else "",
        fontSize = 13.sp,
        color = if (s.probe.canMultiPart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
    Text(stringResource(R.string.network_mode), fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            NetworkMode.DUAL to R.string.mode_dual, NetworkMode.WIFI_ONLY to R.string.mode_wifi,
            NetworkMode.MOBILE_ONLY to R.string.mode_mobile, NetworkMode.AUTO to R.string.mode_auto
        ).forEach { (m, label) ->
            FilterChip(mode == m, { onMode(m) }, { Text(stringResource(label)) })
        }
    }
    Text(stringResource(R.string.schedule), fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0L to "0", 15L to "15 min", 60L to "1 h", 180L to "3 h", 480L to "8 h").forEach { (min, label) ->
            FilterChip(delayMs == min * 60_000L, { onDelay(min * 60_000L) }, {
                Text(if (min == 0L) stringResource(R.string.start_now) else label)
            })
        }
    }
    Spacer(Modifier.height(4.dp))
}
