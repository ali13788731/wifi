package com.example.netspeed.feature.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.DownloadCard
import com.example.netspeed.core.ui.SoftCard
import com.example.netspeed.core.ui.categoryLabel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel, onOpenFile: (String) -> Unit, onShare: (String) -> Unit) {
    val all by vm.downloads.collectAsState()
    var f by remember { mutableStateOf(HistoryFilter()) }
    var panel by rememberSaveable { mutableStateOf(false) }
    val shown = remember(all, f) { applyFilter(all, f) }
    val cats = listOf("video", "audio", "image", "archive", "app", "document", "other")

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.history_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            OutlinedButton({ vm.manager.clearFinished() }) { Text(stringResource(R.string.clear_finished), fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                f.query, { f = f.copy(query = it) }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (f.query.isNotEmpty()) IconButton({ f = f.copy(query = "") }) { Icon(Icons.Rounded.Close, null) } },
                shape = MaterialTheme.shapes.large
            )
            IconButton({ panel = !panel }) {
                BadgedBox(badge = { if (f.activeCount > 0) Badge { Text(f.activeCount.toString()) } }) {
                    Icon(Icons.Rounded.FilterList, stringResource(R.string.filters))
                }
            }
        }

        if (panel) ModalBottomSheet(onDismissRequest = { panel = false }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.filters), fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    Label(R.string.by_category)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cats.forEach { c ->
                            FilterChip(c in f.categories, {
                                f = f.copy(categories = if (c in f.categories) f.categories - c else f.categories + c)
                            }, { Text(categoryLabel(c)) })
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
                    OutlinedButton({ f = HistoryFilter() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.f_reset)) }
                Button({ panel = false }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.stats_apply)) }
            }
        }

        val sumW = shown.sumOf { it.wifiBytes }; val sumM = shown.sumOf { it.mobileBytes }
        Text(
            stringResource(R.string.f_summary, shown.size, Format.bytes(sumW), Format.bytes(sumM)),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 10.dp, 16.dp, 0.dp)
        )

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.id }) { e ->
                val exists = remember(e.resultUri) { e.status != "COMPLETED" || vm.storage.exists(e.resultUri) }
                DownloadCard(
                    e, null, {}, {}, {}, onRetry = { vm.manager.retry(e.id) },
                    onOpen = { if (exists) e.resultUri?.let(onOpenFile) },
                    onShare = { if (exists) e.resultUri?.let(onShare) },
                    onDelete = { vm.manager.remove(e.id, true) }
                )
                if (!exists) Text(
                    stringResource(R.string.file_missing), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
            }
        }
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
