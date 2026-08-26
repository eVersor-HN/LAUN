package com.eversorhn.laun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.data.BatteryOptimization
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/** Shown once on first run, only if not already exempt — some OEM battery managers kill a
 *  backgrounded launcher's process outright instead of just pausing it, which then has to fully
 *  reload the installed-app list the next time it's brought back (briefly rendering every tile
 *  empty in the process). Requesting this exemption is what Pie Launcher does for the same
 *  reason: a launcher is meant to be resident all the time, and reloading it is more expensive
 *  than leaving it alone. */
@Composable
fun BatteryHintSheet(immersiveEnabled: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HideSystemBarsWhileShown(immersiveEnabled)

        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(LaunColors.bg2)
                .border(1.dp, LaunColors.border)
                .padding(20.dp)
        ) {
            Text(
                text = "STAY LOADED",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = "Exempt LAUNCHER from battery optimization so your phone's OS doesn't kill it in the " +
                    "background — otherwise coming back to it can force a full reload, briefly showing an " +
                    "empty grid.",
                color = LaunColors.fg,
                fontFamily = HeadFontFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
            )
            Text(
                text = "EXEMPT NOW",
                color = LaunColors.fg,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, LaunColors.fg)
                    .clickable {
                        BatteryOptimization.requestDisable(context)
                        onDismiss()
                    }
                    .padding(vertical = 10.dp)
            )
            Text(
                text = "LATER",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .border(1.dp, LaunColors.border)
                    .clickable { onDismiss() }
                    .padding(vertical = 10.dp)
            )
        }
    }
}
