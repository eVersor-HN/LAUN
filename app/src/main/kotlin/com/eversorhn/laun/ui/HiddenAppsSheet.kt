package com.eversorhn.laun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/**
 * Settings → SYSTEM → HIDDEN APPS: every app currently hidden from the swipe-up search list (see
 * [AppLaunchSearchSheet]'s long-press), one row each. Tapping a row restores just that one app
 * immediately — no multi-select-then-confirm step, since restoring is harmless and reversible
 * with another long-press in search.
 */
@Composable
fun HiddenAppsSheet(
    hiddenApps: List<AppInfo>,
    immersiveEnabled: Boolean,
    onUnhide: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HideSystemBarsWhileShown(immersiveEnabled)

        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .heightIn(max = 520.dp)
                .background(LaunColors.bg2)
                .border(1.dp, LaunColors.border)
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "HIDDEN APPS (${hiddenApps.size})",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "DONE",
                    color = LaunColors.fg,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }

            if (hiddenApps.isEmpty()) {
                Text(
                    text = "No hidden apps. Long-press an app in search to hide it.",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(hiddenApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUnhide(app) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app.label,
                                color = LaunColors.fg,
                                fontFamily = HeadFontFamily,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "SHOW",
                                color = LaunColors.dim,
                                fontFamily = MonoFontFamily,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
