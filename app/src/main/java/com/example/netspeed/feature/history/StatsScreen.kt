package com.example.netspeed.feature.history

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.Dot
import com.example.netspeed.core.ui.LocalNetColors
import com.example.netspeed.core.ui.NetSplitBar
import com.example.netspeed.core.ui.SoftCard
import com.example.netspeed.core.ui.SplitLegend
import com.example.netspeed.core.ui.categoryLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Real data usage: how much came through Wi-Fi, mobile data, and both, for a chosen period. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(vm: AppViewModel) {
    val all by vm.downloads.collectAsState()
    val c = LocalNetColors.current
    var range by rememberSaveable { mutableStateOf(TimeRange.MONTH) }
    var cat by rememberSaveable { mutableStateOf("all") }
    val since = startOf(range)
    val rows = remember(all, range, cat) {
        all.filter { it.status == "COMPLETED" && it.time >= since && (cat == "all" || it.category == cat) }
    }
    val w = rows.sumOf { it.wifiBytes }; val m = rows.sumOf { it.mobileBytes }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.stats_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TimeRange.TODAY to R.string.r_today, TimeRange.WEEK to R.string.r_week, TimeRange.MONTH to R.string.r_month,
                TimeRange.YEAR to R.string.r_year, TimeRange.ALL to R.string.all).forEach { (k, l) ->
                FilterChip(range == k, { range = k }, { Text(stringResource(l)) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(cat == "all", { cat = "all" }, { Text(stringResource(R.string.all)) })
            listOf("video", "audio", "image", "archive", "app", "document", "other").forEach { k ->
                FilterChip(cat == k, { cat = k }, { Text(categoryLabel(k)) })
            }
        }

        SoftCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.stats_total), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.bytes(w + m), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                NetSplitBar(w, m)
                SplitLegend(w, m)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigNumber(stringResource(R.string.wifi), Format.bytes(w), c.wifi, Modifier.weight(1f))
            BigNumber(stringResource(R.string.mobile), Format.bytes(m), c.mobile, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigNumber(stringResource(R.string.completed), rows.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            BigNumber(stringResource(R.string.stats_biggest), Format.bytes(rows.maxOfOrNull { it.totalBytes } ?: 0), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }

        // ---- per-day stacked bars (last 14 days with data in the chosen range) ----
        val fmt = remember { SimpleDateFormat("MM/dd", Locale.US) }
        val byDay = rows.groupBy { fmt.format(Date(it.time)) }
            .map { (d, l) -> Triple(d, l.sumOf { it.wifiBytes }, l.sumOf { it.mobileBytes }) }
            .sortedBy { it.first }.takeLast(14)
        if (byDay.isNotEmpty()) {
            SoftCard {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.stats_daily), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    val max = byDay.maxOf { it.second + it.third }.coerceAtLeast(1).toFloat()
                    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                        val slot = size.width / byDay.size
                        val bw = slot * .56f
                        byDay.forEachIndexed { i, (_, wb, mb) ->
                            val x = i * slot + (slot - bw) / 2
                            val hw = wb / max * size.height; val hm = mb / max * size.height
                            drawRoundRect(c.mobile, Offset(x, size.height - hw - hm), Size(bw, hm), CornerRadius(6f))
                            drawRoundRect(c.wifi, Offset(x, size.height - hw), Size(bw, hw), CornerRadius(6f))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(byDay.first().first, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(byDay.last().first, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ---- by category ----
        val byCat = rows.groupBy { it.category }.map { (k, l) -> k to l.sumOf { it.wifiBytes + it.mobileBytes } }
            .sortedByDescending { it.second }
        if (byCat.isNotEmpty() && cat == "all") {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.by_category), fontWeight = FontWeight.SemiBold)
                    byCat.forEach { (k, b) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Dot(MaterialTheme.colorScheme.primary, 8.dp)
                            Text("  " + categoryLabel(k), Modifier.weight(1f), fontSize = 13.sp)
                            Text(Format.bytes(b), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        if (rows.isEmpty()) Text(stringResource(R.string.stats_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigNumber(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    SoftCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
