package com.eversorhn.laun

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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

    // Because MainActivity is launchMode="singleTask" and already resumed whenever the user is
    // sitting on the home screen, a repeated "go home" gesture (swiping up again while a sheet is
    // open) never triggers onStop/onPause — Android just redelivers the HOME intent to this same
    // running instance via onNewIntent. The ON_STOP-based reset in LauncherScreen can't see that,
    // so any open sheet was staying stuck open forever. Bumping this on every onNewIntent gives
    // Compose a signal to reset regardless of which path "coming home" took.
    private var homeResetSignal by mutableIntStateOf(0)

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
                        onImmersiveEnabledChange = { applyImmersiveMode(it) },
                        resetSignal = homeResetSignal
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        homeResetSignal++
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
