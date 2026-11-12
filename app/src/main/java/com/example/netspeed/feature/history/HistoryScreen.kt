package com.example.netspeed.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.DownloadCard
import com.example.netspeed.core.ui.categoryLabel

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(vm: AppViewModel, onOpenFile: (String) -> Unit, onShare: (String) -> Unit) {
    val all by vm.downloads.collectAsState()
    var cat by remember { mutableStateOf("all") }
    val done = all.filter { it.status == "COMPLETED" }
    val shown = if (cat == "all") done else done.filter { it.category == cat }
    val cats = listOf("video", "audio", "image", "archive", "app", "document", "other")

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.history_title), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            OutlinedButton({ vm.manager.clearFinished() }) { Text(stringResource(R.string.clear_finished), fontSize = 12.sp) }
        }
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(cat == "all", { cat = "all" }, { Text(stringResource(R.string.all)) })
            cats.filter { c -> done.any { it.category == c } }.forEach { c ->
                FilterChip(cat == c, { cat = c }, { Text(categoryLabel(c)) })
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.id }) { e ->
                val exists = remember(e.resultUri) { vm.storage.exists(e.resultUri) }
                DownloadCard(
                    e, null, {}, {}, {}, {},
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
