package com.example.netspeed.feature.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Scaffold
import com.example.netspeed.R
import com.example.netspeed.core.network.NetState
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.DownloadCard
import com.example.netspeed.core.ui.LocalNetColors
import com.example.netspeed.core.ui.SpeedHero
import com.example.netspeed.core.ui.StatTile

@Composable
fun HomeScreen(vm: AppViewModel, onAdd: () -> Unit, onOpenFile: (String) -> Unit, onShare: (String) -> Unit) {
    val downloads by vm.downloads.collectAsState()
    val live by vm.live.collectAsState()
    val net by vm.net.collectAsState()
    val ctx = LocalContext.current
    val running = live.values.filter { it.status.name == "RUNNING" }
    val active = downloads.filter { it.status in setOf("RUNNING", "PROBING", "VERIFYING") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.add_download)) }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SpeedHero(
                    running.sumOf { it.totalSpeedBps }, running.sumOf { it.wifi.speedBps },
                    running.sumOf { it.mobile.speedBps }
                )
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
            item {
                NetworkCard(net.wifi, net.mobile, net.dualReady) {
                    ctx.startActivity(
                        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            if (active.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.no_downloads), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            stringResource(R.string.no_downloads_hint), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                        )
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
private fun NetworkCard(wifi: NetState, mobile: NetState, dual: Boolean, onOpenSettings: () -> Unit) {
    val c = LocalNetColors.current
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NetPill(Icons.Rounded.Wifi, stringResource(R.string.wifi), wifi, c.wifi, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                NetPill(Icons.Rounded.SignalCellularAlt, stringResource(R.string.mobile), mobile, c.mobile, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (dual) c.ok else c.warn))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (dual) R.string.dual_ready else R.string.dual_not_ready),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
            if (!dual) {
                Text(
                    stringResource(R.string.dual_help), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
                )
                TextButton(onOpenSettings) { Text(stringResource(R.string.open_net_settings)) }
            }
        }
    }
}

@Composable
private fun NetPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, s: NetState,
    color: Color, modifier: Modifier
) {
    val c = LocalNetColors.current
    Column(
        modifier.clip(MaterialTheme.shapes.small).background(color.copy(alpha = .14f)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        val (txt, col) = when {
            s.usable && s.validated -> stringResource(R.string.diag_validated) to c.ok
            s.usable -> stringResource(R.string.diag_connected) to c.warn
            else -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(txt, fontSize = 12.sp, color = col)
        s.ssid?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
