package com.example.netspeed.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.downloader.NetStats
import com.example.netspeed.core.network.NetState
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.LocalNetColors

/**
 * Distinguishes four things per network: available, connected, internet-validated, and
 * actively transferring download bytes (the last one comes from real byte counters).
 */
@Composable
fun DiagnosticsScreen(vm: AppViewModel) {
    val net by vm.net.collectAsState()
    val live by vm.live.collectAsState()
    val c = LocalNetColors.current
    val running = live.values.filter { it.status.name == "RUNNING" }
    fun agg(pick: (com.example.netspeed.core.downloader.LiveProgress) -> NetStats, kind: com.example.netspeed.core.network.NetKind) =
        NetStats(
            kind, running.sumOf { pick(it).speedBps }, running.sumOf { pick(it).bytes },
            running.sumOf { pick(it).activeConnections }, running.flatMap { pick(it).segments }
        )
    val w = agg({ it.wifi }, com.example.netspeed.core.network.NetKind.WIFI)
    val m = agg({ it.mobile }, com.example.netspeed.core.network.NetKind.MOBILE)
    val totalBytes = w.bytes + m.bytes
    val started = running.minOfOrNull { it.startedAt } ?: 0L
    val dur = if (started > 0) (System.currentTimeMillis() - started) / 1000 else -1L

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.diag_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        NetDiag(stringResource(R.string.wifi), net.wifi, w, c.wifi, true)
        NetDiag(stringResource(R.string.mobile), net.mobile, m, c.mobile, false)
        Card(
            Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.dual_network), fontWeight = FontWeight.Bold)
                Row2(stringResource(R.string.total_speed), Format.speed(w.speedBps + m.speedBps))
                Row2(stringResource(R.string.diag_total_bytes), Format.bytes(totalBytes))
                Row2(stringResource(R.string.diag_duration), Format.eta(dur))
                val sum = (w.bytes + m.bytes).coerceAtLeast(1)
                Row2(stringResource(R.string.diag_utilization),
                    "${stringResource(R.string.wifi)} ${Format.pct((w.bytes * 100 / sum).toInt())}  /  ${stringResource(R.string.mobile)} ${Format.pct((m.bytes * 100 / sum).toInt())}")
                Row2(stringResource(R.string.dual_network), stringResource(if (net.dualReady) R.string.yes else R.string.no))
            }
        }
    }
}

@Composable
private fun NetDiag(title: String, s: NetState, st: NetStats, color: Color, showSsid: Boolean) {
    val yes = stringResource(R.string.yes); val no = stringResource(R.string.no)
    fun b(v: Boolean) = if (v) yes else no
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
            if (showSsid) Row2(stringResource(R.string.diag_ssid), s.ssid ?: "—")
            Row2(stringResource(R.string.diag_available), b(s.available))
            Row2(stringResource(R.string.diag_connected), b(s.connected))
            Row2(stringResource(R.string.diag_validated), b(s.validated))
            Row2(stringResource(R.string.diag_transferring), b(st.speedBps > 0))
            Row2(stringResource(R.string.diag_speed), Format.speed(st.speedBps))
            Row2(stringResource(R.string.diag_bytes), Format.bytes(st.bytes))
            Row2(stringResource(R.string.diag_connections), Format.n(st.activeConnections))
            Row2(stringResource(R.string.diag_segments), st.segments.sorted().joinToString(", ").ifEmpty { "—" })
        }
    }
}

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}
