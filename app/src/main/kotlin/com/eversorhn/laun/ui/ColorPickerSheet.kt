package com.eversorhn.laun.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily
import com.eversorhn.laun.ui.theme.TILE_COLOR_PALETTE

/**
 * Long-press tile-coloring popover — direct port of the 20-swatch color-popover in demo.html,
 * anchored near the press position instead of a full-screen sheet, matching its "small popover
 * that appears where you pressed" feel.
 */
@Composable
fun ColorPickerSheet(
    anchor: Offset,
    slotApps: List<AppInfo>,
    onPick: (String) -> Unit,
    onReset: () -> Unit,
    onEditApps: () -> Unit,
    onClearTile: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val singleApp = slotApps.singleOrNull()
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: androidx.compose.ui.unit.IntRect,
                windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: androidx.compose.ui.unit.IntSize
            ): IntOffset {
                var x = anchor.x.toInt()
                var y = anchor.y.toInt()
                if (x + popupContentSize.width > windowSize.width - 8) x = windowSize.width - popupContentSize.width - 8
                if (y + popupContentSize.height > windowSize.height - 8) y = windowSize.height - popupContentSize.height - 8
                if (x < 8) x = 8
                if (y < 8) y = 8
                return IntOffset(x, y)
            }
        },
        onDismissRequest = onDismiss,
        // Default Popup isn't focusable, so it never intercepts the system back button — a back
        // press would instead fall through to the grid's own BackHandler and close the grid while
        // leaving this popover stranded on screen. focusable = true makes it own back-press
        // dismissal like every other sheet in the app (all of which are Dialogs, focusable by default).
        properties = androidx.compose.ui.window.PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .width(216.dp)
                .background(Color(0xF20D0D0D))
                .border(1.dp, LaunColors.border)
                .padding(12.dp)
        ) {
            Text(
                text = "CHOOSE TILE COLOR",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 9.5.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(TILE_COLOR_PALETTE) { color ->
                    ColorSwatch(color) { onPick(color.toHex()) }
                }
            }
            ActionRow(text = "RESET COLOR", onClick = onReset)
            if (singleApp != null) {
                ActionRow(text = "APP INFO", onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", singleApp.packageName, null))
                    )
                    onDismiss()
                })
                ActionRow(text = "UNINSTALL", onClick = {
                    context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${singleApp.packageName}")))
                    onDismiss()
                })
            }
            ActionRow(text = "EDIT APPS", onClick = onEditApps)
            ActionRow(text = if (singleApp != null) "CLEAR TILE" else "CLEAR FOLDER", onClick = onClearTile)
        }
    }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = LaunColors.dim,
        fontFamily = MonoFontFamily,
        fontSize = 9.5.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .border(1.dp, LaunColors.border)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun ColorSwatch(color: Color, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
    )
}

private fun Color.toHex(): String =
    "#%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
