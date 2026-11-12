package com.example.netspeed.feature.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.netspeed.core.ui.CardShape
import com.example.netspeed.core.ui.Dot
import com.example.netspeed.core.ui.DownloadCard
import com.example.netspeed.core.ui.LocalNetColors
import com.example.netspeed.core.ui.NetSplitBar
import com.example.netspeed.core.ui.SoftCard
import com.example.netspeed.core.ui.SpeedChart
import com.example.netspeed.core.ui.StatTile

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd, shape = CardShape,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.add_download), fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.splash_tagline), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onDetails) { Icon(Icons.Rounded.Insights, stringResource(R.string.diag_title)) }
                }
            }

            // ---- hero: combined real speed ----
            item {
                Column(
                    Modifier.fillMaxWidth().clip(CardShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF0B5FFF), Color(0xFF6A3DF0)))).padding(22.dp)
                ) {
                    Text(stringResource(R.string.total_speed), color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
                    Text(Format.speed(wSpeed + mSpeed), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
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

            // ---- each network, live ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NetCard(Icons.Rounded.Wifi, stringResource(R.string.wifi), net.wifi, wSpeed, wBytes,
                        history.map { it.first }, c.wifi, Modifier.weight(1f))
                    NetCard(Icons.Rounded.SignalCellularAlt, stringResource(R.string.mobile), net.mobile, mSpeed, mBytes,
                        history.map { it.second }, c.mobile, Modifier.weight(1f))
                }
            }

            // ---- both networks must be on ----
            item {
                SoftCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(if (net.dualReady) c.ok else c.warn)
                            Spacer(Modifier.width(8.dp))
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

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(stringResource(R.string.active), active.size.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.pending),
                        downloads.count { it.status in setOf("QUEUED", "PAUSED") }.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.completed),
                        downloads.count { it.status == "COMPLETED" }.toString(), Modifier.weight(1f))
                }
            }

            if (active.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.no_downloads), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(stringResource(R.string.no_downloads_hint), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            items(active, key = { it.id }) { e ->
                DownloadCard(
                    e, live[e.id],
                    onPause = { vm.manager.pause(e.id) }, onResume = { vm.manager.resume(e.id) },
                    onCancel = { vm.manager.cancel(e.id) }, onRetry = { vm.manager.retry(e.id) },
                    onOpen = { e.resultUri?.let(onOpenFile) }, onShare = { e.resultUri?.let(onShare) },
                    onDelete = { vm.manager.remove(e.id, false) }
                )
            }
        }
    }
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
                Box(Modifier.size(32.dp).clip(CardShape).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
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
            Text(Format.speed(speed), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(stringResource(R.string.home_session_bytes, Format.bytes(bytes)), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            SpeedChart(history, color)
            s.ssid?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
