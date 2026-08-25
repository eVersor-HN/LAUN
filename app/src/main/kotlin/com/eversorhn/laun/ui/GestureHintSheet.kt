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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/**
 * Second first-run hint, shown right after [DefaultLauncherHintSheet] is dismissed — the app's
 * one non-obvious gesture (long-press the open grid's empty background to reach Settings) has no
 * other affordance anywhere in the UI, so this is the only place a new user learns it exists.
 */
@Composable
fun GestureHintSheet(immersiveEnabled: Boolean, onDismiss: () -> Unit) {
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
                text = "SETTINGS",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = "Long-press an empty spot on the home screen to reach Settings — works whether the grid is open or closed.",
                color = LaunColors.fg,
                fontFamily = HeadFontFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
            )
            Text(
                text = "GOT IT",
                color = LaunColors.fg,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, LaunColors.fg)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp)
            )
        }
    }
}
