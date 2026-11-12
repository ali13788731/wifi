package com.example.netspeed.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.common.Texts
import com.example.netspeed.core.downloader.RequestOptions
import com.example.netspeed.core.ui.AddState
import com.example.netspeed.core.ui.AppViewModel

/**
 * New-download sheet. Nothing is downloaded until the user taps Start. A link is accepted only if
 * both networks are up AND the server proves HTTP Range (otherwise it cannot use two networks).
 * If [autoCheck] is true (link came from the clipboard / another app) the link is checked at once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddDownloadSheet(vm: AppViewModel, initialUrl: String?, autoCheck: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val net by vm.net.collectAsState()
    val state = vm.addState
    var url by remember { mutableStateOf(initialUrl ?: "") }
    var ua by remember { mutableStateOf(RequestOptions.DEFAULT_UA) }
    var referer by remember { mutableStateOf("") }
    var cookies by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var delayMs by remember { mutableLongStateOf(0L) }

    fun opts() = RequestOptions(
        userAgent = ua.ifBlank { RequestOptions.DEFAULT_UA },
        referer = referer.ifBlank { null }, cookies = cookies.ifBlank { null },
        username = user.ifBlank { null }, password = pass.ifBlank { null }
    )

    LaunchedEffect(Unit) {
        vm.resetAdd()
        if (autoCheck && !initialUrl.isNullOrBlank()) vm.inspect(initialUrl, opts())
    }
    LaunchedEffect(state) { if (state is AddState.Ready) name = state.probe.fileName ?: "" }

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

            if (!net.dualReady) {
                val code = when {
                    !net.wifi.usable && !net.mobile.usable -> "NO_BOTH"
                    !net.wifi.usable -> "NO_WIFI"
                    else -> "NO_MOBILE"
                }
                Text(Texts.error(ctx, code), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            OutlinedTextField(
                url, { url = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.url)) },
                singleLine = true, enabled = state !is AddState.Checking,
                shape = MaterialTheme.shapes.medium
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
                is AddState.Error -> Text(Texts.error(ctx, state.code), color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                is AddState.Ready -> {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.file_name)) })
                    Text(stringResource(R.string.file_size) + ": " + Format.bytes(state.probe.totalBytes), fontSize = 14.sp)
                    Text(stringResource(R.string.range_ok), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.schedule), fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0L to R.string.start_now, 15L to 0, 60L to 0, 180L to 0, 480L to 0).forEach { (min, res) ->
                            FilterChip(delayMs == min * 60_000L, { delayMs = min * 60_000L }, {
                                Text(if (res != 0) stringResource(res) else if (min < 60) "$min ${stringResource(R.string.minutes)}" else "${min / 60} ${stringResource(R.string.hours)}")
                            })
                        }
                    }
                }
                AddState.Idle -> {}
            }

            if (state is AddState.Ready) {
                Button(
                    onClick = { vm.confirmAdd(state, name, delayMs); onDismiss() },
                    Modifier.fillMaxWidth(), enabled = net.dualReady
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
}
