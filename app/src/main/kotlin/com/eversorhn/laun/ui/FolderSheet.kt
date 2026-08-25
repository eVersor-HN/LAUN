package com.eversorhn.laun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/** Tapping a folder tile (a slot with more than one app) opens this instead of launching. */
@Composable
fun FolderSheet(
    apps: List<AppInfo>,
    immersiveEnabled: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HideSystemBarsWhileShown(immersiveEnabled)

        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .heightIn(max = 420.dp)
                .background(LaunColors.bg2)
                .border(1.dp, LaunColors.border)
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "FOLDER (${apps.size})",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLaunch(app)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = app.label,
                            color = LaunColors.fg,
                            fontFamily = HeadFontFamily,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
