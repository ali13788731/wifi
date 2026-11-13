package com.example.netspeed.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.netspeed.R

private class MenuActions(
    val copy: (() -> Unit)?, val paste: (() -> Unit)?, val cut: (() -> Unit)?, val selectAll: (() -> Unit)?
)

/**
 * Wraps a text field with our own long-press menu (Cut / Copy / Paste / Select all), shown in the
 * app language. It always works, also inside bottom sheets where the system menu can be missing.
 */
@Composable
fun ToolbarField(content: @Composable () -> Unit) {
    val menu = remember { mutableStateOf<MenuActions?>(null) }
    val toolbar = remember {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = if (menu.value != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

            override fun hide() { menu.value = null }

            override fun showMenu(
                rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?
            ) {
                menu.value = MenuActions(onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
            }
        }
    }
    Box {
        CompositionLocalProvider(LocalTextToolbar provides toolbar) { content() }
        menu.value?.let { m ->
            val up = with(LocalDensity.current) { 56.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopCenter, offset = IntOffset(0, -up),
                onDismissRequest = { menu.value = null },
                properties = PopupProperties(focusable = false)
            ) {
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, shadowElevation = 10.dp) {
                    Row(Modifier.padding(horizontal = 4.dp)) {
                        m.cut?.let { a -> TextButton({ a(); menu.value = null }) { Text(stringResource(R.string.tb_cut)) } }
                        m.copy?.let { a -> TextButton({ a(); menu.value = null }) { Text(stringResource(R.string.tb_copy)) } }
                        m.paste?.let { a -> TextButton({ a(); menu.value = null }) { Text(stringResource(R.string.tb_paste)) } }
                        m.selectAll?.let { a -> TextButton({ a() }) { Text(stringResource(R.string.tb_select_all)) } }
                    }
                }
            }
        }
    }
}
