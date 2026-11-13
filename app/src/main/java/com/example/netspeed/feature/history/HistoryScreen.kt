package com.example.netspeed.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.common.DateText
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.common.Texts
import com.example.netspeed.core.database.DownloadEntity
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.IconTile
import com.example.netspeed.core.ui.LocalNetColors
import com.example.netspeed.core.ui.NetSplitBar
import com.example.netspeed.core.ui.Pill
import com.example.netspeed.core.ui.SoftCard
import com.example.netspeed.core.ui.SplitLegend
import com.example.netspeed.core.ui.categoryIcon
import com.example.netspeed.core.ui.categoryLabel
import com.example.netspeed.core.ui.statusLabel

private val FINISHED = setOf("COMPLETED", "FAILED", "CANCELED")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel, onOpenFile: (String) -> Unit, onShare: (String) -> Unit) {
    val all by vm.downloads.collectAsState()
    var f by remember { mutableStateOf(HistoryFilter()) }
    var panel by rememberSaveable { mutableStateOf(false) }
    val finished = remember(all) { all.filter { it.status in FINISHED } }
    val shown = remember(all, f) { applyFilter(all, f) }
    val cats = listOf("video", "audio", "image", "archive", "app", "document", "other")
    val c = LocalNetColors.current

    Column(Modifier.fillMaxSize()) {
        // ---- header
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.history_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                if (finished.isNotEmpty()) Text(
                    stringResource(R.string.history_count, finished.size), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // only when there is something to clear
            if (finished.isNotEmpty()) TextButton({ vm.manager.clearFinished() }) {
                Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.clear_finished), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (finished.isEmpty()) {
            // Nothing yet: no search, no filter, no clear button — just a calm empty state.
            EmptyState(Icons.Rounded.History, R.string.history_empty_title, R.string.history_empty_hint, Modifier.weight(1f))
        } else {
            // ---- search + filter
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    f.query, { f = f.copy(query = it) }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (f.query.isNotEmpty()) IconButton({ f = f.copy(query = "") }) { Icon(Icons.Rounded.Close, null) } },
                    shape = MaterialTheme.shapes.large
                )
                FilledTonalIconButton({ panel = true }, Modifier.size(52.dp)) {
                    BadgedBox(badge = { if (f.activeCount > 0) Badge { Text(Format.n(f.activeCount)) } }) {
                        Icon(Icons.Rounded.Tune, stringResource(R.string.filters))
                    }
                }
            }

            // ---- totals for what is shown
            val sumW = shown.sumOf { it.wifiBytes }; val sumM = shown.sumOf { it.mobileBytes }
            if (shown.isNotEmpty()) SoftCard(Modifier.padding(16.dp, 6.dp, 16.dp, 0.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.history_total), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Format.bytes(sumW + sumM), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(stringResource(R.string.history_count, shown.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    NetSplitBar(sumW, sumM)
                    SplitLegend(sumW, sumM)
                }
            }

            if (shown.isEmpty()) {
                EmptyState(Icons.Rounded.SearchOff, R.string.no_results_title, R.string.no_results_hint, Modifier.weight(1f)) {
                    OutlinedButton({ f = HistoryFilter() }) { Text(stringResource(R.string.f_reset)) }
                }
            } else {
                val today = remember(shown) { DateText.startOfDay(System.currentTimeMillis()) }
                val dayMs = 24L * 3600 * 1000
                val grouped = remember(shown) { shown.groupBy { DateText.startOfDay(it.time) } }
                val todayLabel = stringResource(R.string.day_today)
                val yLabel = stringResource(R.string.day_yesterday)
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    grouped.forEach { (day, list) ->
                        item(key = "h$day") {
                            Text(
                                when (day) { today -> todayLabel; today - dayMs -> yLabel; else -> DateText.date(day) },
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                            )
                        }
                        items(list, key = { it.id }) { e ->
                            val exists = remember(e.resultUri) { e.status != "COMPLETED" || vm.storage.exists(e.resultUri) }
                            HistoryItem(
                                e, exists,
                                onOpen = { if (exists) e.resultUri?.let(onOpenFile) },
                                onShare = { if (exists) e.resultUri?.let(onShare) },
                                onRetry = { vm.manager.retry(e.id) },
                                onDelete = { vm.manager.remove(e.id, true) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (panel) ModalBottomSheet(
        onDismissRequest = { panel = false },
        // open fully at once; no need to drag it up
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.filters), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Label(R.string.by_category)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { k ->
                    FilterChip(k in f.categories, {
                        f = f.copy(categories = if (k in f.categories) f.categories - k else f.categories + k)
                    }, { Text(categoryLabel(k)) })
                }
            }
            Label(R.string.f_status)
            Chips(listOf("ALL" to R.string.all, "COMPLETED" to R.string.status_completed,
                "FAILED" to R.string.status_failed, "CANCELED" to R.string.status_canceled), f.status) { f = f.copy(status = it) }
            Label(R.string.f_period)
            Chips(listOf(TimeRange.ALL to R.string.all, TimeRange.TODAY to R.string.r_today, TimeRange.WEEK to R.string.r_week,
                TimeRange.MONTH to R.string.r_month, TimeRange.YEAR to R.string.r_year), f.range) { f = f.copy(range = it) }
            Label(R.string.f_size)
            Chips(listOf(SizeBucket.ANY to R.string.all, SizeBucket.SMALL to R.string.sz_small, SizeBucket.MEDIUM to R.string.sz_medium,
                SizeBucket.LARGE to R.string.sz_large, SizeBucket.HUGE to R.string.sz_huge), f.size) { f = f.copy(size = it) }
            Label(R.string.f_network)
            Chips(listOf(NetBias.ANY to R.string.all, NetBias.WIFI_MAJOR to R.string.nb_wifi, NetBias.MOBILE_MAJOR to R.string.nb_mobile), f.bias) { f = f.copy(bias = it) }
            Label(R.string.f_sort)
            Chips(listOf(SortBy.NEWEST to R.string.so_newest, SortBy.OLDEST to R.string.so_oldest, SortBy.LARGEST to R.string.so_largest,
                SortBy.SMALLEST to R.string.so_smallest, SortBy.NAME to R.string.so_name), f.sort) { f = f.copy(sort = it) }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ f = HistoryFilter() }, Modifier.weight(1f)) { Text(stringResource(R.string.f_reset)) }
                Button({ panel = false }, Modifier.weight(1f)) { Text(stringResource(R.string.stats_apply)) }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    e: DownloadEntity, exists: Boolean,
    onOpen: () -> Unit, onShare: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val c = LocalNetColors.current
    val tint = when (e.status) { "COMPLETED" -> c.ok; "FAILED" -> cs.error; else -> cs.onSurfaceVariant }
    SoftCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(categoryIcon(e.category), tint, 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        categoryLabel(e.category) + " • " + Format.bytes(e.totalBytes) + " • " + DateText.time(e.time),
                        fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (e.status != "COMPLETED") { Spacer(Modifier.width(8.dp)); Pill(statusLabel(e.status), tint) }
            }
            if (e.status == "COMPLETED" && e.wifiBytes + e.mobileBytes > 0) {
                Spacer(Modifier.height(10.dp))
                NetSplitBar(e.wifiBytes, e.mobileBytes)
                Spacer(Modifier.height(6.dp))
                SplitLegend(e.wifiBytes, e.mobileBytes)
            }
            if (e.status == "FAILED") Text(Texts.error(androidx.compose.ui.platform.LocalContext.current, e.error),
                fontSize = 12.sp, color = cs.error, modifier = Modifier.padding(top = 8.dp))
            if (!exists) Text(stringResource(R.string.file_missing), fontSize = 12.sp, color = cs.error, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (e.status == "COMPLETED" && exists) {
                    TextButton(onOpen) { Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.open)) }
                    TextButton(onShare) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share)) }
                } else if (e.status == "FAILED" || e.status == "CANCELED") {
                    TextButton(onRetry) { Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.retry)) }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onDelete) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), tint = cs.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, hint: Int, modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconTile(icon, MaterialTheme.colorScheme.primary, 84.dp)
        Spacer(Modifier.height(18.dp))
        Text(stringResource(title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(hint), fontSize = 13.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(14.dp)); action() }
    }
}

@Composable
private fun Label(res: Int) = Text(stringResource(res), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(items: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (k, label) -> FilterChip(selected == k, { onSelect(k) }, { Text(stringResource(label)) }) }
    }
}
