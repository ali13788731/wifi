package com.example.netspeed.feature.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.database.DownloadEntity
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.DownloadCard
import com.example.netspeed.core.ui.IconTile

private val ACTIVE = setOf("RUNNING", "PROBING", "VERIFYING")

/** Queue + active + paused + failed downloads (everything that is not finished successfully). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(vm: AppViewModel, onOpenFile: (String) -> Unit, onShare: (String) -> Unit) {
    val all by vm.downloads.collectAsState()
    val live by vm.live.collectAsState()
    val list = all.filter { it.status != "COMPLETED" && it.status != "CANCELED" }
    var toDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var blocked by remember { mutableStateOf<String?>(null) }
    // Starting / continuing needs BOTH Wi-Fi and mobile data: say which one is off.
    val guard: (() -> Unit) -> Unit = { action -> vm.missingNetwork()?.let { blocked = it } ?: action() }

    val nActive = list.count { it.status in ACTIVE }
    val nQueued = list.count { it.status == "QUEUED" }
    val nPaused = list.count { it.status == "PAUSED" }
    val nFailed = list.count { it.status == "FAILED" }
    // a filter whose list became empty falls back to "all" instead of showing a blank page
    val effective = when (filter) {
        "ACTIVE" -> if (nActive > 0) filter else "ALL"
        "QUEUED" -> if (nQueued > 0) filter else "ALL"
        "PAUSED" -> if (nPaused > 0) filter else "ALL"
        "FAILED" -> if (nFailed > 0) filter else "ALL"
        else -> "ALL"
    }
    val shown = when (effective) {
        "ACTIVE" -> list.filter { it.status in ACTIVE }
        "QUEUED" -> list.filter { it.status == "QUEUED" }
        "PAUSED" -> list.filter { it.status == "PAUSED" }
        "FAILED" -> list.filter { it.status == "FAILED" }
        else -> list
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp)) {
            Text(stringResource(R.string.nav_downloads), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            if (list.isNotEmpty()) Text(
                stringResource(R.string.dl_summary, list.size), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Stop-all / resume-all exist only while there is something to stop or resume.
        AnimatedVisibility(
            visible = nActive > 0 || nPaused > 0,
            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (nActive > 0) FilledTonalButton({ vm.manager.pauseAll() }, Modifier.weight(1f), shape = CircleShape) {
                    Icon(Icons.Rounded.Pause, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.pause_all_btn), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (nPaused > 0) Button({ guard { vm.manager.resumeAll() } }, Modifier.weight(1f), shape = CircleShape) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.resume_all_btn), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (list.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Chip("ALL", effective, R.string.dl_filter_all, list.size) { filter = it } }
                if (nActive > 0) item { Chip("ACTIVE", effective, R.string.dl_filter_active, nActive) { filter = it } }
                if (nQueued > 0) item { Chip("QUEUED", effective, R.string.dl_filter_queued, nQueued) { filter = it } }
                if (nPaused > 0) item { Chip("PAUSED", effective, R.string.dl_filter_paused, nPaused) { filter = it } }
                if (nFailed > 0) item { Chip("FAILED", effective, R.string.dl_filter_failed, nFailed) { filter = it } }
            }
        }

        if (list.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IconTile(Icons.Rounded.CloudDownload, MaterialTheme.colorScheme.primary, 84.dp)
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.dl_empty_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.dl_empty_hint), fontSize = 13.sp, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else LazyColumn(
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shown, key = { it.id }) { e ->
                Box(Modifier.animateItemPlacement()) {
                    DownloadCard(
                        e, live[e.id],
                        onPause = { vm.manager.pause(e.id) }, onResume = { guard { vm.manager.resume(e.id) } },
                        onCancel = { vm.manager.cancel(e.id) }, onRetry = { guard { vm.manager.retry(e.id) } },
                        onOpen = { e.resultUri?.let(onOpenFile) }, onShare = { e.resultUri?.let(onShare) },
                        onDelete = { toDelete = e }
                    )
                }
            }
        }
    }

    blocked?.let { com.example.netspeed.core.ui.NetBlockedDialog(it) { blocked = null } }

    toDelete?.let { e ->
        var alsoFile by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.remove_from_list)) },
            text = {
                if (e.resultUri != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(alsoFile, { alsoFile = it })
                    Text(stringResource(R.string.delete_file))
                } else Text(e.fileName)
            },
            confirmButton = { TextButton({ vm.manager.remove(e.id, alsoFile); toDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton({ toDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun Chip(key: String, selected: String, label: Int, count: Int, onSelect: (String) -> Unit) {
    FilterChip(selected == key, { onSelect(key) }, { Text(stringResource(label) + "  " + com.example.netspeed.core.common.Format.n(count)) })
}
