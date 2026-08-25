package com.eversorhn.laun.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

private const val REPO_URL = "https://github.com/eVersor-HN/LAUN"

@Composable
fun AboutSheet(immersiveEnabled: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Read from the installed package instead of a hardcoded literal — a hand-maintained version
    // string here silently drifted three releases behind build.gradle.kts's actual versionName.
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HideSystemBarsWhileShown(immersiveEnabled)

        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(LaunColors.bg2)
                .border(1.dp, LaunColors.border)
                .padding(20.dp)
        ) {
            Text(
                text = "LAUNCHER",
                color = LaunColors.fg,
                fontFamily = HeadFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Text(
                text = versionName?.let { "v$it" } ?: "",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            InfoRow(label = "DEVELOPER", value = "Marco Aurelio Fattizzo")
            InfoRow(label = "LICENSE", value = "GPLv3 — Open Source")
            InfoRow(label = "PACKAGE", value = "com.eversorhn.laun")

            Text(
                text = "GITHUB REPOSITORY",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
            )
            Text(
                text = REPO_URL.removePrefix("https://"),
                color = LaunColors.fg,
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, LaunColors.border)
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            color = LaunColors.dim,
            fontFamily = MonoFontFamily,
            fontSize = 9.sp,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            color = LaunColors.fg,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
