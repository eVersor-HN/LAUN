package com.eversorhn.laun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.data.InstalledAppsRepository
import com.eversorhn.laun.data.LauncherPrefs
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch

/**
 * Root screen — direct port of demo.html's #stage: an empty screen you tap to reveal the
 * honeycomb, plus the status bar, settings and color-picker chrome around it.
 */
@Composable
fun LauncherScreen(
    prefs: LauncherPrefs,
    onImmersiveEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by prefs.settings.collectAsState(initial = com.eversorhn.laun.data.LauncherSettings())

    val repository = remember { InstalledAppsRepository(context) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) { apps = repository.loadApps() }

    var isOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var colorPickerApp by remember { mutableStateOf<AppInfo?>(null) }
    var colorPickerAnchor by remember { mutableStateOf(Offset.Zero) }

    BackHandler(enabled = isOpen) { isOpen = false }

    val visibleApps = remember(apps, settings.hexCount) { apps.take(settings.hexCount) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LaunColors.bg)
    ) {
        if (!isOpen) {
            Text(
                text = "// TAP TO INITIALIZE //",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 13.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        HexGrid(
            apps = visibleApps,
            hexSizeDp = settings.hexSizeDp,
            tileColors = settings.tileColors,
            isOpen = isOpen,
            onOpen = { isOpen = true },
            onCloseBackground = { isOpen = false },
            onLongPressApp = { app, pos ->
                colorPickerApp = app
                colorPickerAnchor = pos
            },
            onLaunch = { app -> repository.launch(app.packageName) },
            modifier = Modifier.fillMaxSize()
        )

        if (settings.hudVisible) {
            StatusBar(
                isOpen = isOpen,
                appCount = visibleApps.size,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .systemBarsPadding()
                    .padding(20.dp)
            )
        }

        FloatingActionButton(
            onClick = { showSettings = true },
            containerColor = LaunColors.bg2,
            contentColor = LaunColors.fg,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
        }
    }

    colorPickerApp?.let { app ->
        ColorPickerSheet(
            anchor = colorPickerAnchor,
            onPick = { hex ->
                scope.launch { prefs.setTileColor(app.packageName, hex) }
                colorPickerApp = null
            },
            onReset = {
                scope.launch { prefs.setTileColor(app.packageName, null) }
                colorPickerApp = null
            },
            onDismiss = { colorPickerApp = null }
        )
    }

    if (showSettings) {
        SettingsSheet(
            hexSizeDp = settings.hexSizeDp,
            onHexSizeChange = { scope.launch { prefs.setHexSize(it) } },
            hexCountIndex = settings.hexCountIndex,
            onHexCountIndexChange = { scope.launch { prefs.setHexCountIndex(it) } },
            hudVisible = settings.hudVisible,
            onHudVisibleChange = { scope.launch { prefs.setHudVisible(it) } },
            immersiveEnabled = settings.immersiveEnabled,
            onImmersiveEnabledChange = {
                scope.launch { prefs.setImmersiveEnabled(it) }
                onImmersiveEnabledChange(it)
            },
            onDismiss = { showSettings = false }
        )
    }
}
