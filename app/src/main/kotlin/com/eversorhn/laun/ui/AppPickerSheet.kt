package com.eversorhn.laun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/**
 * App picker for an empty grid slot — tapping an empty tile opens this instead of launching
 * anything. Lists installed apps not already assigned to another slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    apps: List<AppInfo>,
    onPick: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LaunColors.bg2,
        contentColor = LaunColors.fg
    ) {
        Text(
            text = "APP HINZUFÜGEN",
            color = LaunColors.dim,
            fontFamily = MonoFontFamily,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        if (apps.isEmpty()) {
            Text(
                text = "Keine weiteren Apps verfügbar.",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app) }
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
