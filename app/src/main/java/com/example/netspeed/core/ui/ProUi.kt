package com.example.netspeed.core.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import com.example.netspeed.core.license.LicenseManager

/** The purchase manager of the running activity (null in previews). Lets any screen open the Pro dialog. */
val LocalLicense = compositionLocalOf<LicenseManager?> { null }

/** Small "PRO" pill shown next to a locked feature. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.pro_name), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Brand.gradient).padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

/** A settings row that is locked in the free version: title + PRO badge, opens the Pro dialog when tapped. */
@Composable
fun LockedRow(title: String, hint: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            if (hint != null) Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ProBadge()
    }
}

/** Banner: "Upgrade to Pro" for free users, "Pro is active" for Pro users. */
@Composable
fun ProCard(isPro: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brand.gradientWide)
            .clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (isPro) R.string.pro_active else R.string.pro_card_title), color = Color.White,
                fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(if (isPro) R.string.pro_active_sub else R.string.pro_card_sub), color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
        }
    }
}

/** Full-page lock used for a whole Pro-only screen (usage statistics). */
@Composable
fun ProLockedScreen(featureText: Int, onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconTile(Icons.Rounded.Lock, MaterialTheme.colorScheme.primary, 84.dp)
            Text(stringResource(featureText), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(stringResource(R.string.pro_locked_body), fontSize = 14.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onUnlock, Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.pro_unlock), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/**
 * The upgrade dialog. [reason] is a string resource explaining which feature the user just tapped
 * (or null when opened from the banner). The purchase itself goes through Cafe Bazaar.
 */
@Composable
fun ProDialog(reason: Int?, onDismiss: () -> Unit) {
    val license = LocalLicense.current
    val ctx = LocalContext.current
    // bought (or restored) while the dialog is open: close it
    LaunchedEffect(license?.state) { if (license?.state == LicenseManager.State.LICENSED) onDismiss() }
    val features = listOf(
        R.string.pro_feat_dual, R.string.pro_feat_limiter, R.string.pro_feat_cap, R.string.pro_feat_schedule,
        R.string.pro_feat_advanced, R.string.pro_feat_history, R.string.pro_feat_stats, R.string.pro_feat_more
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Star, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.pro_dialog_title), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reason != null) Text(stringResource(reason), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(stringResource(R.string.pro_dialog_sub), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                features.forEach { Text("•  " + stringResource(it), fontSize = 13.sp) }
                when (license?.problem) {
                    LicenseManager.State.NO_BAZAAR -> Text(stringResource(R.string.lic_no_bazaar), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    LicenseManager.State.ERROR -> Text(stringResource(R.string.lic_failed), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    LicenseManager.State.UNLICENSED -> Text(stringResource(R.string.lic_not_found), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    else -> {}
                }
                OutlinedButton({ license?.check(byUser = true) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.lic_recheck)) }
                if (license?.problem == LicenseManager.State.NO_BAZAAR) OutlinedButton({
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cafebazaar.ir/download/bazaar.apk"))) }
                }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.lic_install_bazaar)) }
            }
        },
        confirmButton = { Button({ license?.buy() }) { Text(stringResource(R.string.lic_buy), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.not_now)) } }
    )
}
