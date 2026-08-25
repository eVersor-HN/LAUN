package com.eversorhn.laun.ui

import android.content.Intent
import android.provider.Settings
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
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/** Shown once on first run — offers to jump straight to the system's default-home-app picker. */
@Composable
fun DefaultLauncherHintSheet(immersiveEnabled: Boolean, onDismiss: () -> Unit) {
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
                text = "HOME SCREEN",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = "Set LAUNCHER as default so your home screen looks the way you set it up.",
                color = LaunColors.fg,
                fontFamily = HeadFontFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
            )
            Text(
                text = "SET NOW",
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
                        context.startActivity(
                            Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
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
