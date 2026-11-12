package com.example.netspeed.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.netspeed.core.database.DownloadEntity
import com.example.netspeed.core.ui.AppViewModel
import com.example.netspeed.core.ui.DownloadCard

/** Queue + active + paused + failed downloads (everything that is not finished successfully). */
@Composable
fun DownloadsScreen(vm: AppViewModel, onOpenFile: (String) -> Unit, onShare: (String) -> Unit) {
    val all by vm.downloads.collectAsState()
    val live by vm.live.collectAsState()
    val list = all.filter { it.status != "COMPLETED" }
    var toDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.nav_downloads), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            OutlinedButton({ vm.manager.pauseAll() }) { Text(stringResource(R.string.pause_all_btn), fontSize = 12.sp) }
            OutlinedButton({ vm.manager.resumeAll() }) { Text(stringResource(R.string.resume_all_btn), fontSize = 12.sp) }
        }
        if (list.isEmpty()) {
            Text(
                stringResource(R.string.no_downloads), Modifier.padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(list, key = { it.id }) { e ->
                DownloadCard(
                    e, live[e.id],
                    onPause = { vm.manager.pause(e.id) }, onResume = { vm.manager.resume(e.id) },
                    onCancel = { vm.manager.cancel(e.id) }, onRetry = { vm.manager.retry(e.id) },
                    onOpen = { e.resultUri?.let(onOpenFile) }, onShare = { e.resultUri?.let(onShare) },
                    onDelete = { toDelete = e }
                )
            }
        }
    }

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
