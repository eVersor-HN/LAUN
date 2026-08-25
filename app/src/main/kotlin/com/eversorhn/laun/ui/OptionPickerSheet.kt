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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/**
 * A dedicated full picker for a list of named options — a 2-column grid instead of a horizontal
 * scroller, so a growing option count (tile animations, backgrounds, ...) stays scannable instead
 * of turning into an ever-longer sideways strip. [preview] renders above the grid so picking a
 * different option shows its effect immediately.
 */
@Composable
fun OptionPickerSheet(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    immersiveEnabled: Boolean,
    preview: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HideSystemBarsWhileShown(immersiveEnabled)

        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .heightIn(max = 620.dp)
                .background(LaunColors.bg2)
                .border(1.dp, LaunColors.border)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
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
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }

            preview()

            Column(modifier = Modifier.padding(top = 16.dp)) {
                options.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowOptions.forEach { name ->
                            val index = options.indexOf(name)
                            val selected = index == selectedIndex
                            Text(
                                text = name,
                                color = if (selected) LaunColors.fg else LaunColors.dim,
                                fontFamily = MonoFontFamily,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, if (selected) LaunColors.fg else LaunColors.border)
                                    .clickable { onSelect(index) }
                                    .padding(vertical = 12.dp, horizontal = 6.dp)
                            )
                        }
                        if (rowOptions.size == 1) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
