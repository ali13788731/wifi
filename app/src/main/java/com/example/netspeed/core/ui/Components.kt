package com.example.netspeed.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.common.Texts
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

val CardShape = RoundedCornerShape(22.dp)

@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(), shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) { content() }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) =
    Box(Modifier.size(size).clip(CircleShape).background(color))

/** Thick rounded progress bar with the text drawn in the middle of it. */
@Composable
fun ProgressWithLabel(progress: Float, label: String, modifier: Modifier = Modifier, indeterminate: Boolean = false) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    Box(
        modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .10f)),
        contentAlignment = Alignment.Center
    ) {
        if (!indeterminate) Box(
            Modifier.fillMaxHeight().fillMaxWidth(p).align(Alignment.CenterStart)
                .background(Brush.horizontalGradient(listOf(Color(0xFF0B5FFF), Color(0xFF6A3DF0))))
        )
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (p > .5f) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

/** Horizontal two-colour bar: share of Wi-Fi vs mobile bytes. */
@Composable
fun NetSplitBar(wifiBytes: Long, mobileBytes: Long, modifier: Modifier = Modifier) {
    val c = LocalNetColors.current
    val sum = (wifiBytes + mobileBytes).coerceAtLeast(1).toFloat()
    Row(modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))) {
        if (wifiBytes > 0) Box(Modifier.weight(wifiBytes / sum).fillMaxHeight().background(c.wifi))
        if (mobileBytes > 0) Box(Modifier.weight(mobileBytes / sum).fillMaxHeight().background(c.mobile))
        if (wifiBytes + mobileBytes == 0L) Spacer(Modifier.weight(1f))
    }
}

@Composable
fun SplitLegend(wifiBytes: Long, mobileBytes: Long) {
    val c = LocalNetColors.current
    val sum = (wifiBytes + mobileBytes).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(c.wifi, 8.dp); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.wifi) + " " + Format.bytes(wifiBytes) + " (" + (wifiBytes * 100 / sum) + "%)", fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(c.mobile, 8.dp); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.mobile) + " " + Format.bytes(mobileBytes) + " (" + (mobileBytes * 100 / sum) + "%)", fontSize = 12.sp)
        }
    }
}

/** Smooth area chart of the last samples (bytes/s). */
@Composable
fun SpeedChart(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(256 * 1024L).toFloat()
    Canvas(modifier.fillMaxWidth().height(54.dp)) {
        if (values.size < 2) return@Canvas
        val dx = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * dx, size.height - (v / max) * size.height * .92f - 2f) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val mid = (pts[i - 1].x + pts[i].x) / 2
                cubicTo(mid, pts[i - 1].y, mid, pts[i].y, pts[i].x, pts[i].y)
            }
        }
        val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .35f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier, shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One download: name, type, size, progress in the bar, remaining, ETA, per-network speed and bytes. */
@Composable
fun DownloadCard(
    e: DownloadEntity, live: LiveProgress?,
    onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit,
    onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val c = LocalNetColors.current
    val done = live?.doneBytes ?: e.doneBytes
    val total = live?.totalBytes ?: e.totalBytes
    val pct = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val running = e.status in setOf("RUNNING", "PROBING", "VERIFYING")
    val finished = e.status == "COMPLETED" || e.status == "CANCELED"
    val wBytes = live?.wifi?.bytes ?: e.wifiBytes
    val mBytes = live?.mobile?.bytes ?: e.mobileBytes

    SoftCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(e.fileName.substringAfterLast('.', "?").take(4).uppercase(), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(categoryLabel(e.category) + " • " + Format.bytes(total) + " • " + statusLabel(e.status),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            if (!finished) {
                Spacer(Modifier.height(12.dp))
                ProgressWithLabel(
                    pct, "${(pct * 100).toInt()}%  •  ${Format.bytes(done)} / ${Format.bytes(total)}",
                    indeterminate = running && total <= 0
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.remaining) + " " + Format.bytes((total - done).coerceAtLeast(0)), fontSize = 12.sp)
                    if (live != null && running) Text(stringResource(R.string.eta) + " " + Format.eta(live.etaSeconds), fontSize = 12.sp)
                }
            }
            if (running && live != null) {
                Spacer(Modifier.height(8.dp))
                NetLine(c.wifi, stringResource(R.string.wifi), live.wifi.speedBps, wBytes)
                Spacer(Modifier.height(4.dp))
                NetLine(c.mobile, stringResource(R.string.mobile), live.mobile.speedBps, mBytes)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.total_speed) + ": " + Format.speed(live.totalSpeedBps),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Texts.notice(ctx, live.notice)?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = c.warn, fontWeight = FontWeight.Medium)
                }
            }
            if (e.status == "COMPLETED" && wBytes + mBytes > 0) {
                Spacer(Modifier.height(12.dp))
                NetSplitBar(wBytes, mBytes)
                Spacer(Modifier.height(6.dp))
                SplitLegend(wBytes, mBytes)
            }
            if (e.status == "FAILED") Text(
                Texts.error(ctx, e.error), fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun NetLine(color: Color, name: String, speed: Long, bytes: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Dot(color, 8.dp)
        Spacer(Modifier.width(6.dp))
        Text(name, fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(Format.speed(speed) + "  •  " + Format.bytes(bytes), fontSize = 12.sp)
    }
}
