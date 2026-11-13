package com.example.netspeed.feature.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.common.LinkDetector
import com.example.netspeed.core.common.Texts
import com.example.netspeed.core.downloader.RequestOptions
import com.example.netspeed.core.ui.AddState
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.Brand
import com.example.netspeed.core.ui.IconTile
import com.example.netspeed.core.ui.LocalNetColors
import com.example.netspeed.core.ui.NetBlockedDialog
import com.example.netspeed.core.ui.ProBadge
import com.example.netspeed.core.ui.ProDialog
import com.example.netspeed.core.ui.SoftCard
import com.example.netspeed.core.ui.ToolbarField
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import com.example.netspeed.core.ui.categoryIcon

/**
 * New-download sheet. Nothing is downloaded until the user taps Start. A link is accepted only if
 * both networks are up AND the server proves HTTP Range (otherwise it cannot use two networks).
 * If [autoCheck] is true (link came from the clipboard / another app) the link is checked at once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddDownloadSheet(vm: AppViewModel, initialUrl: String?, autoCheck: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
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
    var blocked by remember { mutableStateOf<String?>(null) }
    val openedAt = remember { System.currentTimeMillis() }
    var resumes by remember { mutableIntStateOf(0) }
    val allowSingle by vm.settings.collectAsState()
    val isPro by vm.isPro.collectAsState()
    var proReason by remember { mutableStateOf<Int?>(null) }
    // Free: one network is always enough. Pro: both, unless the user allowed single-network mode.
    val single = !isPro || allowSingle.allowSingleNetwork

    fun opts() = RequestOptions(
        userAgent = ua.ifBlank { RequestOptions.DEFAULT_UA },
        referer = referer.ifBlank { null }, cookies = cookies.ifBlank { null },
        username = user.ifBlank { null }, password = pass.ifBlank { null }
    )

    LaunchedEffect(Unit) {
        vm.resetAdd()
        if (autoCheck && !initialUrl.isNullOrBlank()) vm.inspect(initialUrl, opts())
    }
    // A different link arrived while the sheet is open (copied in another app / shared in).
    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank() && initialUrl != url) {
            url = initialUrl; vm.resetAdd()
            if (autoCheck) vm.inspect(initialUrl, opts())
        }
    }
    // The user went to the browser, copied a link and came back: paste it by itself.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(resumes) {
        if (resumes == 0) return@LaunchedEffect
        // Android lets an app read the clipboard only once its window has focus: try a few times.
        repeat(5) {
            delay(450)
            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
            val stamp = runCatching { cm?.primaryClipDescription?.timestamp ?: 0L }.getOrDefault(0L)
            val link = runCatching {
                LinkDetector.extract(cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString())
            }.getOrNull()
            if (link != null && stamp > openedAt && link != url) {
                url = link; vm.resetAdd(); vm.inspect(link, opts())
                return@LaunchedEffect
            }
            if (link != null && stamp > 0) return@LaunchedEffect   // focus is there, nothing new to paste
        }
    }
    LaunchedEffect(state) { if (state is AddState.Ready) name = state.probe.fileName ?: "" }

    ModalBottomSheet(
        onDismissRequest = { vm.resetAdd(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(Brand.gradient), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CloudDownload, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.add_download), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(if (isPro) R.string.add_subtitle else R.string.add_subtitle_free), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- both networks must be on: show each one's state
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NetStatus(Icons.Rounded.Wifi, stringResource(R.string.wifi), net.wifi.usable, single, Modifier.weight(1f))
                NetStatus(Icons.Rounded.SignalCellularAlt, stringResource(R.string.mobile), net.mobile.usable, single, Modifier.weight(1f))
            }
            // One network is off and single-network mode is not allowed yet: offer the setting right here.
            if (isPro && vm.missingNetwork() != null) TextButton({ vm.settingsRequest = "downloads"; onDismiss() }) {
                Text(stringResource(R.string.single_hint_btn))
            }

            // ---- link
            ToolbarField { OutlinedTextField(
                url, { url = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.url)) },
                singleLine = true, enabled = state !is AddState.Checking,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                trailingIcon = {
                    IconButton({
                        LinkDetector.extract(clipboard.getText()?.text)?.let { url = it; vm.resetAdd() }
                    }) { Icon(Icons.Rounded.ContentPaste, stringResource(R.string.paste)) }
                }
            ) }

            // ---- advanced options (collapsible)
            SoftCard {
                Column(Modifier.animateContentSize()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { if (isPro) advanced = !advanced else proReason = R.string.pro_why_advanced }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.advanced), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (!isPro) { ProBadge(); Spacer(Modifier.width(8.dp)) }
                        Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedVisibility(advanced && isPro) {
                        Column(Modifier.padding(14.dp, 0.dp, 14.dp, 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(ua, { ua = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.user_agent)) }, shape = MaterialTheme.shapes.medium)
                            OutlinedTextField(referer, { referer = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.referer)) }, shape = MaterialTheme.shapes.medium)
                            OutlinedTextField(cookies, { cookies = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.cookies)) }, shape = MaterialTheme.shapes.medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(user, { user = it }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.username)) }, shape = MaterialTheme.shapes.medium)
                                OutlinedTextField(pass, { pass = it }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.password)) },
                                    visualTransformation = PasswordVisualTransformation(), shape = MaterialTheme.shapes.medium)
                            }
                        }
                    }
                }
            }

            // ---- result of the link check
            when (state) {
                is AddState.Checking -> SoftCard {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.checking), fontWeight = FontWeight.Medium)
                    }
                }
                is AddState.Error -> Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = .12f)).padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(10.dp))
                    Text(Texts.error(ctx, state.code), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                is AddState.Ready -> SoftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(categoryIcon(Format.category(name.ifBlank { state.probe.fileName ?: "" }, state.probe.mimeType)),
                                MaterialTheme.colorScheme.primary, 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.file_size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Format.bytes(state.probe.totalBytes), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        ToolbarField { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.file_name)) },
                            shape = MaterialTheme.shapes.medium) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = LocalNetColors.current.ok, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.range_ok), fontSize = 12.sp, color = LocalNetColors.current.ok, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.start_when), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (!isPro) { Spacer(Modifier.width(8.dp)); ProBadge() }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0L, 15L, 60L, 180L, 480L).forEach { min ->
                                FilterChip(delayMs == min * 60_000L, {
                                    if (min > 0 && !isPro) proReason = R.string.pro_why_schedule else delayMs = min * 60_000L
                                }, {
                                    Text(
                                        when {
                                            min == 0L -> stringResource(R.string.start_now)
                                            min < 60 -> Format.n(min) + " " + stringResource(R.string.minutes)
                                            else -> Format.n(min / 60) + " " + stringResource(R.string.hours)
                                        }
                                    )
                                })
                            }
                        }
                    }
                }
                AddState.Idle -> {}
            }

            // ---- actions
            val ready = state as? AddState.Ready
            val checking = state is AddState.Checking
            BigButton(
                text = stringResource(
                    when {
                        ready == null -> R.string.check_link
                        delayMs > 0 -> R.string.schedule
                        else -> R.string.start_now
                    }
                ),
                icon = if (ready == null) Icons.Rounded.Link else Icons.Rounded.CloudDownload,
                enabled = !checking
            ) {
                if (ready == null) vm.inspect(url, opts())
                else {
                    // Starting needs BOTH networks: say exactly which one is off instead of doing nothing.
                    val missing = vm.missingNetwork()
                    if (missing != null) blocked = missing
                    else if (vm.confirmAdd(ready, name, delayMs)) onDismiss()
                }
            }
            TextButton({ vm.resetAdd(); onDismiss() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
        }
    }

    blocked?.let { NetBlockedDialog(it, { vm.settingsRequest = "downloads"; onDismiss() }) { blocked = null } }
    if (proReason != null && !isPro) ProDialog(proReason) { proReason = null }
}

@Composable
private fun NetStatus(icon: ImageVector, label: String, on: Boolean, optional: Boolean, modifier: Modifier) {
    val c = LocalNetColors.current
    val color = if (on) c.ok else if (optional) c.warn else MaterialTheme.colorScheme.error
    Row(
        modifier.clip(MaterialTheme.shapes.medium).background(color.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(if (on) R.string.net_status_on else R.string.net_status_off), fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

/** Full-width gradient call-to-action button. */
@Composable
private fun BigButton(text: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).alpha(if (enabled) 1f else .5f)
            .clip(CircleShape).background(Brand.gradient)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
