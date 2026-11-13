package com.example.netspeed.feature.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.common.Texts
import com.example.netspeed.core.network.NetState
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.Brand
import com.example.netspeed.core.ui.CardShape
import com.example.netspeed.core.ui.Dot
import com.example.netspeed.core.ui.DownloadCard
import com.example.netspeed.core.ui.IconTile
import com.example.netspeed.core.ui.LocalNetColors
import com.example.netspeed.core.ui.NetSplitBar
import com.example.netspeed.core.ui.SoftCard
import com.example.netspeed.core.ui.SpeedChart
import com.example.netspeed.core.ui.StatTile
import kotlinx.coroutines.delay

/** Soft entrance: each block fades and slides up a little after the one before it. */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(index * 80L); a.animateTo(1f, tween(500)) }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 36f }) { content() }
}

@Composable
fun HomeScreen(
    vm: AppViewModel, onAdd: () -> Unit, onOpenFile: (String) -> Unit, onShare: (String) -> Unit,
    onDetails: () -> Unit
) {
    val ctx = LocalContext.current
    val c = LocalNetColors.current
    val downloads by vm.downloads.collectAsState()
    val live by vm.live.collectAsState()
    val net by vm.net.collectAsState()
    val history by vm.speedHistory.collectAsState()
    val running = live.values.filter { it.status.name == "RUNNING" }
    val active = downloads.filter { it.status in setOf("RUNNING", "PROBING", "VERIFYING") }
    val wSpeed = running.sumOf { it.wifi.speedBps }
    val mSpeed = running.sumOf { it.mobile.speedBps }
    val wBytes = running.sumOf { it.wifi.bytes }
    val mBytes = running.sumOf { it.mobile.bytes }
    var blocked by remember { mutableStateOf<String?>(null) }
    val guard: (() -> Unit) -> Unit = { action -> vm.missingNetwork()?.let { blocked = it } ?: action() }

    blocked?.let { com.example.netspeed.core.ui.NetBlockedDialog(it) { blocked = null } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd, shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.add_download), fontWeight = FontWeight.Bold) }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Reveal(0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_name), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                            Text(stringResource(R.string.splash_tagline), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalIconButton(onDetails) { Icon(Icons.Rounded.Insights, stringResource(R.string.diag_title)) }
                    }
                }
            }

            // ---- hero: combined real speed on a gradient card with soft decorative circles
            item {
                Reveal(1) {
                    Box(Modifier.fillMaxWidth().clip(CardShape).background(Brand.gradientWide)) {
                        Canvas(Modifier.matchParentSize()) {
                            drawCircle(Color.White.copy(alpha = .08f), radius = size.height * .85f, center = Offset(size.width * .95f, -size.height * .1f))
                            drawCircle(Color.White.copy(alpha = .06f), radius = size.height * .55f, center = Offset(size.width * .05f, size.height * 1.05f))
                        }
                        Column(Modifier.padding(22.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulseDot(running.isNotEmpty())
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.home_live), color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                            }
                            Text(Format.speed(wSpeed + mSpeed), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(14.dp))
                            val sum = (wSpeed + mSpeed).coerceAtLeast(1)
                            Text(
                                stringResource(R.string.home_share_now, (wSpeed * 100 / sum).toInt(), (mSpeed * 100 / sum).toInt()),
                                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))
                            NetSplitBar(wSpeed, mSpeed)
                        }
                    }
                }
            }

            // ---- each network, live
            item {
                Reveal(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NetCard(Icons.Rounded.Wifi, stringResource(R.string.wifi), net.wifi, wSpeed, wBytes,
                            history.map { it.first }, c.wifi, Modifier.weight(1f))
                        NetCard(Icons.Rounded.SignalCellularAlt, stringResource(R.string.mobile), net.mobile, mSpeed, mBytes,
                            history.map { it.second }, c.mobile, Modifier.weight(1f))
                    }
                }
            }

            // ---- both networks must be on
            item {
                Reveal(3) {
                    SoftCard {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulseDot(net.dualReady, if (net.dualReady) c.ok else c.warn)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(if (net.dualReady) R.string.dual_ready else R.string.dual_not_ready),
                                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                                )
                            }
                            if (!net.dualReady) {
                                val code = when {
                                    !net.wifi.usable && !net.mobile.usable -> "NO_BOTH"
                                    !net.wifi.usable -> "NO_WIFI"
                                    else -> "NO_MOBILE"
                                }
                                Text(Texts.error(ctx, code), fontSize = 13.sp, color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp))
                                Text(stringResource(R.string.dual_help), fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                TextButton({
                                    ctx.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }) { Text(stringResource(R.string.open_net_settings)) }
                            } else {
                                Text(stringResource(R.string.home_how), fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }

            item {
                Reveal(5) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(stringResource(R.string.pending),
                            downloads.count { it.status in setOf("QUEUED", "PAUSED") }.let { Format.n(it) }, Modifier.weight(1f), c.warn)
                        StatTile(stringResource(R.string.completed),
                            Format.n(downloads.count { it.status == "COMPLETED" }), Modifier.weight(1f), c.ok)
                    }
                }
            }

            if (active.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconTile(Icons.Rounded.CloudDownload, MaterialTheme.colorScheme.primary, 64.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.no_downloads), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            items(active, key = { it.id }) { e ->
                DownloadCard(
                    e, live[e.id],
                    onPause = { vm.manager.pause(e.id) }, onResume = { guard { vm.manager.resume(e.id) } },
                    onCancel = { vm.manager.cancel(e.id) }, onRetry = { guard { vm.manager.retry(e.id) } },
                    onOpen = { e.resultUri?.let(onOpenFile) }, onShare = { e.resultUri?.let(onShare) },
                    onDelete = { vm.manager.remove(e.id, false) }
                )
            }
        }
    }
}

/** Small dot that breathes while something is happening. */
@Composable
private fun PulseDot(animate: Boolean, color: Color = Color.White) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(Modifier.size(10.dp).graphicsLayer { alpha = if (animate) a else 0.6f }.clip(CircleShape).background(color))
}


@Composable
private fun NetCard(
    icon: ImageVector, label: String, s: NetState, speed: Long, bytes: Long,
    history: List<Long>, color: Color, modifier: Modifier
) {
    val c = LocalNetColors.current
    SoftCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, color, 34.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    val (txt, col) = when {
                        s.usable && s.validated -> stringResource(R.string.diag_validated) to c.ok
                        s.usable -> stringResource(R.string.diag_connected) to c.warn
                        else -> stringResource(R.string.net_off) to MaterialTheme.colorScheme.error
                    }
                    Text(txt, fontSize = 11.sp, color = col)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(Format.speed(speed), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(stringResource(R.string.home_session_bytes, Format.bytes(bytes)), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            SpeedChart(history, color)
            s.ssid?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
