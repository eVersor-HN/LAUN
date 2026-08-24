package com.eversorhn.laun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eversorhn.laun.data.LauncherPrefs
import com.eversorhn.laun.data.LauncherSettings
import com.eversorhn.laun.ui.LauncherScreen
import com.eversorhn.laun.ui.theme.LauncherTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: LauncherPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs = LauncherPrefs(applicationContext)

        setContent {
            LauncherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val settings by prefs.settings.collectAsState(initial = LauncherSettings())

                    LaunchedEffect(settings.immersiveEnabled) {
                        applyImmersiveMode(settings.immersiveEnabled)
                    }

                    LauncherScreen(
                        prefs = prefs,
                        onImmersiveEnabledChange = { applyImmersiveMode(it) }
                    )
                }
            }
        }
    }

    /** Hides/shows the Android system status + navigation bars — separate from LAUNCHER's own HUD. */
    private fun applyImmersiveMode(enabled: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
