package com.eversorhn.laun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eversorhn.laun.data.RING_COUNTS
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/**
 * Settings panel — direct port of demo.html's bottom-left panel: tile size, symmetric tile count
 * (snapped to the ring totals so the honeycomb is always a complete, symmetric shape), HUD and
 * immersive-mode toggles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    hexSizeDp: Int,
    onHexSizeChange: (Int) -> Unit,
    hexCountIndex: Int,
    onHexCountIndexChange: (Int) -> Unit,
    didShrinkToFit: Boolean,
    hudVisible: Boolean,
    onHudVisibleChange: (Boolean) -> Unit,
    hudShowStatus: Boolean,
    onHudShowStatusChange: (Boolean) -> Unit,
    hudShowClock: Boolean,
    onHudShowClockChange: (Boolean) -> Unit,
    hudShowBattery: Boolean,
    onHudShowBatteryChange: (Boolean) -> Unit,
    hudShowSignal: Boolean,
    onHudShowSignalChange: (Boolean) -> Unit,
    hudShowAppCount: Boolean,
    onHudShowAppCountChange: (Boolean) -> Unit,
    hudShowCursor: Boolean,
    onHudShowCursorChange: (Boolean) -> Unit,
    immersiveEnabled: Boolean,
    onImmersiveEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LaunColors.bg2,
        contentColor = LaunColors.fg
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingRow(label = "GRÖSSE", value = "${hexSizeDp}dp") {
                Slider(
                    value = hexSizeDp.toFloat(),
                    onValueChange = { onHexSizeChange(it.toInt()) },
                    valueRange = 60f..170f,
                    colors = SliderDefaults.colors(
                        thumbColor = LaunColors.fg,
                        activeTrackColor = LaunColors.fg,
                        inactiveTrackColor = LaunColors.border
                    )
                )
            }

            SettingRow(label = "ANZAHL", value = "${RING_COUNTS[hexCountIndex]}") {
                Slider(
                    value = hexCountIndex.toFloat(),
                    onValueChange = { onHexCountIndexChange(it.toInt()) },
                    valueRange = 0f..(RING_COUNTS.size - 1).toFloat(),
                    steps = RING_COUNTS.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = LaunColors.fg,
                        activeTrackColor = LaunColors.fg,
                        inactiveTrackColor = LaunColors.border
                    )
                )
            }

            if (didShrinkToFit) {
                Text(
                    text = "PASST NICHT AUF DEN SCREEN — GRÖSSE AUTOMATISCH VERKLEINERT",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 9.sp,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            ToggleRow(label = "HUD-STATUSLEISTE", checked = hudVisible, onCheckedChange = onHudVisibleChange)
            if (hudVisible) {
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    ToggleRow(label = "STATUS", checked = hudShowStatus, onCheckedChange = onHudShowStatusChange)
                    ToggleRow(label = "UHRZEIT", checked = hudShowClock, onCheckedChange = onHudShowClockChange)
                    ToggleRow(label = "AKKU", checked = hudShowBattery, onCheckedChange = onHudShowBatteryChange)
                    ToggleRow(label = "SIGNAL", checked = hudShowSignal, onCheckedChange = onHudShowSignalChange)
                    ToggleRow(label = "APP-ANZAHL", checked = hudShowAppCount, onCheckedChange = onHudShowAppCountChange)
                    ToggleRow(label = "CURSOR", checked = hudShowCursor, onCheckedChange = onHudShowCursorChange)
                }
            }
            ToggleRow(label = "VOLLBILDMODUS", checked = immersiveEnabled, onCheckedChange = onImmersiveEnabledChange)
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = LaunColors.dim, fontFamily = MonoFontFamily, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(value, color = LaunColors.fg, fontFamily = MonoFontFamily, fontSize = 10.sp)
        }
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = LaunColors.dim, fontFamily = MonoFontFamily, fontSize = 10.sp, letterSpacing = 1.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LaunColors.bg,
                checkedTrackColor = LaunColors.fg,
                uncheckedThumbColor = LaunColors.dim,
                uncheckedTrackColor = LaunColors.border
            )
        )
    }
}
