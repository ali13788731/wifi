package com.example.netspeed.core.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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

fun categoryIcon(c: String): ImageVector = when (c) {
    "video" -> Icons.Rounded.Movie
    "audio" -> Icons.Rounded.MusicNote
    "image" -> Icons.Rounded.Image
    "archive" -> Icons.Rounded.Archive
    "app" -> Icons.Rounded.Android
    "document" -> Icons.Rounded.Description
    else -> Icons.Rounded.AttachFile
}

val CardShape = RoundedCornerShape(24.dp)

/** Rounded card with a hairline border: clean in both light and dark themes. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(), shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) { content() }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) =
    Box(Modifier.size(size).clip(CircleShape).background(color))

/** Rounded square with a soft tinted background and an icon in the middle. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(tint.copy(alpha = .14f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f)) }
}

/** Small coloured pill, e.g. the status of a download. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier.clip(CircleShape).background(color.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
    )
}

/** Thick rounded progress bar with the text drawn in the middle of it. */
@Composable
fun ProgressWithLabel(progress: Float, label: String, modifier: Modifier = Modifier, indeterminate: Boolean = false) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450), label = "progress")
    Box(
        modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)),
        contentAlignment = Alignment.Center
    ) {
        if (!indeterminate) Box(
            Modifier.fillMaxHeight().fillMaxWidth(p).align(Alignment.CenterStart).background(Brand.gradient)
        )
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (p > .5f) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

/** Slim animated gradient bar. */
@Composable
fun SlimProgress(progress: Float, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450), label = "slim")
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(p).clip(CircleShape).background(Brand.gradient))
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
            Text(stringResource(R.string.wifi) + " " + Format.bytes(wifiBytes) + " (" + Format.pct((wifiBytes * 100 / sum).toInt()) + ")", fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(c.mobile, 8.dp); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.mobile) + " " + Format.bytes(mobileBytes) + " (" + Format.pct((mobileBytes * 100 / sum).toInt()) + ")", fontSize = 12.sp)
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
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Card(
        modifier, shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One download: icon, name, status, progress, remaining, ETA, per-network speed and bytes, and
 * clear labelled buttons. EVERY unfinished download has its own Stop (pause) button.
 */
@Composable
fun DownloadCard(
    e: DownloadEntity, live: LiveProgress?,
    onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit,
    onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val c = LocalNetColors.current
    val cs = MaterialTheme.colorScheme
    val done = live?.doneBytes ?: e.doneBytes
    val total = live?.totalBytes ?: e.totalBytes
    val pct = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val running = e.status in setOf("RUNNING", "PROBING", "VERIFYING")
    val finished = e.status == "COMPLETED" || e.status == "CANCELED"
    val wBytes = live?.wifi?.bytes ?: e.wifiBytes
    val mBytes = live?.mobile?.bytes ?: e.mobileBytes
    val tint = when (e.status) {
        "COMPLETED" -> c.ok; "FAILED" -> cs.error; "PAUSED" -> c.warn; "QUEUED" -> cs.onSurfaceVariant; else -> cs.primary
    }

    SoftCard(modifier.animateContentSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(categoryIcon(e.category), tint)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(categoryLabel(e.category) + " • " + Format.bytes(total),
                        fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                Pill(statusLabel(e.status), tint)
            }

            if (!finished) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.pct((pct * 100).toInt()), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(Format.bytes(done) + " / " + Format.bytes(total), fontSize = 12.sp,
                        color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (live != null && running) Text(stringResource(R.string.eta) + " " + Format.eta(live.etaSeconds),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
                SlimProgress(pct)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.remaining) + " " + Format.bytes((total - done).coerceAtLeast(0)),
                    fontSize = 12.sp, color = cs.onSurfaceVariant)
            }

            // Exactly how much came through each network so far (and the live speed while running).
            val showUsage = e.status == "RUNNING" || (!finished && e.status != "VERIFYING" && wBytes + mBytes > 0)
            if (showUsage) {
                Spacer(Modifier.height(12.dp))
                NetUsagePanel(
                    wBytes, mBytes,
                    if (e.status == "RUNNING" && live != null) live.wifi.speedBps else null,
                    if (e.status == "RUNNING" && live != null) live.mobile.speedBps else null
                )
                if (e.status == "RUNNING" && live != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.total_speed) + ": " + Format.speed(live.totalSpeedBps),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                }
            }
            if (running && live != null) Texts.notice(ctx, live.notice)?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = c.warn, fontWeight = FontWeight.Medium)
            }
            if (e.status == "COMPLETED" && wBytes + mBytes > 0) {
                Spacer(Modifier.height(12.dp))
                NetSplitBar(wBytes, mBytes)
                Spacer(Modifier.height(6.dp))
                SplitLegend(wBytes, mBytes)
            }
            if (e.status == "FAILED") Text(
                Texts.error(ctx, e.error), fontSize = 12.sp, color = cs.error, modifier = Modifier.padding(top = 8.dp)
            )

            // ---------------- action buttons
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (e.status) {
                    "RUNNING", "PROBING", "QUEUED" -> ActionButton(Icons.Rounded.Pause, stringResource(R.string.stop_download), onPause, Modifier.weight(1f))
                    "PAUSED" -> ActionButton(Icons.Rounded.PlayArrow, stringResource(R.string.resume), onResume, Modifier.weight(1f), filled = true)
                    "FAILED" -> ActionButton(Icons.Rounded.Refresh, stringResource(R.string.retry), onRetry, Modifier.weight(1f), filled = true)
                    "COMPLETED" -> {
                        ActionButton(Icons.Rounded.OpenInNew, stringResource(R.string.open), onOpen, Modifier.weight(1f), filled = true)
                        ActionButton(Icons.Rounded.Share, stringResource(R.string.share), onShare, Modifier.weight(1f))
                    }
                    else -> Spacer(Modifier.weight(1f))
                }
                if (e.status == "COMPLETED" || e.status == "FAILED" || e.status == "CANCELED")
                    IconButton(onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = cs.onSurfaceVariant) }
                else if (e.status != "VERIFYING")
                    IconButton(onCancel) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel), tint = cs.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier, filled: Boolean = false) {
    if (filled) Button(onClick, modifier, shape = CircleShape, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    } else FilledTonalButton(onClick, modifier, shape = CircleShape, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * Two tiles side by side: Wi-Fi and mobile data. Each shows the exact amount downloaded through
 * that network, its share of the total and (while running) its live speed. A thin bar below
 * shows the split.
 */
@Composable
fun NetUsagePanel(wifiBytes: Long, mobileBytes: Long, wifiSpeed: Long?, mobileSpeed: Long?, modifier: Modifier = Modifier) {
    val c = LocalNetColors.current
    val sum = (wifiBytes + mobileBytes).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NetTile(c.wifi, stringResource(R.string.wifi), wifiBytes, (wifiBytes * 100 / sum).toInt(), wifiSpeed, Modifier.weight(1f))
            NetTile(c.mobile, stringResource(R.string.mobile), mobileBytes, (mobileBytes * 100 / sum).toInt(), mobileSpeed, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        NetSplitBar(wifiBytes, mobileBytes)
    }
}

@Composable
private fun NetTile(color: Color, name: String, bytes: Long, share: Int, speed: Long?, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(color.copy(alpha = .10f)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color, 8.dp); Spacer(Modifier.width(6.dp))
            Text(name, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            Text(Format.pct(share), fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(Format.bytes(bytes), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        if (speed != null) Text(Format.speed(speed), fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/**
 * Explains why a download cannot start/continue: names exactly which network is off. The button
 * on the side opens the setting that lets the user download with only one network.
 */
@Composable
fun NetBlockedDialog(code: String, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.WifiOff, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.blocked_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Texts.blocked(ctx, code))
                androidx.compose.material3.OutlinedButton({
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_net_settings)) }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button({ onDismiss(); onSettings() }) { Text(stringResource(R.string.blocked_settings)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text(stringResource(R.string.ok)) } }
    )
}
