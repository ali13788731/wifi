package com.example.netspeed.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.database.DownloadEntity
import com.example.netspeed.core.downloader.LiveProgress

@Composable
fun statusLabel(status: String): String = stringResource(
    when (status) {
        "QUEUED" -> R.string.status_queued
        "PROBING" -> R.string.status_probing
        "RUNNING" -> R.string.status_running
        "PAUSED" -> R.string.status_paused
        "VERIFYING" -> R.string.status_verifying
        "COMPLETED" -> R.string.status_completed
        "FAILED" -> R.string.status_failed
        else -> R.string.status_canceled
    }
)

@Composable
fun categoryLabel(c: String): String = stringResource(
    when (c) {
        "video" -> R.string.cat_video; "audio" -> R.string.cat_audio; "image" -> R.string.cat_image
        "archive" -> R.string.cat_archive; "app" -> R.string.cat_app; "document" -> R.string.cat_document
        else -> R.string.cat_other
    }
)

/** Big gradient card: combined speed plus a bar that shows the Wi-Fi / Mobile split. */
@Composable
fun SpeedHero(total: Long, wifi: Long, mobile: Long, modifier: Modifier = Modifier) {
    val c = LocalNetColors.current
    val sum = (wifi + mobile).coerceAtLeast(1)
    Card(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF0B5FFF), Color(0xFF6A3DF0))))
                .padding(22.dp)
        ) {
            Text(stringResource(R.string.total_speed), color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
            Text(
                Format.speed(total), color = Color.White,
                fontSize = 38.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))
            ) {
                val w = wifi.toFloat() / sum
                val m = mobile.toFloat() / sum
                if (w > 0f) Box(Modifier.weight(w).fillMaxHeight().background(c.wifi))
                if (m > 0f) Box(Modifier.weight(m).fillMaxHeight().background(c.mobile))
                if (w == 0f && m == 0f) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LegendDot(c.wifi, stringResource(R.string.wifi) + "  " + Format.speed(wifi))
                LegendDot(c.mobile, stringResource(R.string.mobile) + "  " + Format.speed(mobile))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier, shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One download: name, type, size, progress, remaining, ETA, per-network speeds and actions. */
@Composable
fun DownloadCard(
    e: DownloadEntity, live: LiveProgress?,
    onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit,
    onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalNetColors.current
    val done = live?.doneBytes ?: e.doneBytes
    val total = live?.totalBytes ?: e.totalBytes
    val pct = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val running = e.status in setOf("RUNNING", "PROBING", "VERIFYING")
    Card(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        e.fileName.substringAfterLast('.', "?").take(4).uppercase(),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        categoryLabel(e.category) + " • " + Format.bytes(total) + " • " + statusLabel(e.status),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (e.status) {
                    "RUNNING", "PROBING" -> IconButton(onPause) { Icon(Icons.Rounded.Pause, stringResource(R.string.pause)) }
                    "PAUSED", "QUEUED" -> IconButton(onResume) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.resume)) }
                    "FAILED" -> IconButton(onRetry) { Icon(Icons.Rounded.Refresh, stringResource(R.string.retry)) }
                    "COMPLETED" -> {
                        IconButton(onOpen) { Icon(Icons.Rounded.OpenInNew, stringResource(R.string.open)) }
                        IconButton(onShare) { Icon(Icons.Rounded.Share, stringResource(R.string.share)) }
                    }
                }
                if (e.status == "COMPLETED" || e.status == "FAILED" || e.status == "CANCELED")
                    IconButton(onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                else IconButton(onCancel) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
            }
            if (e.status != "COMPLETED" && e.status != "CANCELED") {
                Spacer(Modifier.height(10.dp))
                if (running && total <= 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(
                    progress = { pct }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(pct * 100).toInt()}%  •  " + stringResource(R.string.remaining) + " " +
                        Format.bytes((total - done).coerceAtLeast(0)), fontSize = 12.sp)
                    if (live != null) Text(stringResource(R.string.eta) + " " + Format.eta(live.etaSeconds), fontSize = 12.sp)
                }
                if (live != null && running) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.wifi) + " " + Format.speed(live.wifi.speedBps), fontSize = 12.sp, color = c.wifi, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.mobile) + " " + Format.speed(live.mobile.speedBps), fontSize = 12.sp, color = c.mobile, fontWeight = FontWeight.Medium)
                        Text(Format.speed(live.totalSpeedBps), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    live.message?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (e.status == "FAILED" && e.error != null)
                Text(e.error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
