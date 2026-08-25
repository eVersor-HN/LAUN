package com.eversorhn.laun.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * ModalBottomSheet/Dialog overlays render in their own separate Android Window, which does not
 * inherit MainActivity's immersive (hidden system bars) state — Android shows the status/nav
 * bars by default for any new top-level window. Call this from inside such an overlay's content
 * to re-hide them for as long as it's shown.
 */
@Composable
internal fun HideSystemBarsWhileShown(enabled: Boolean) {
    if (!enabled) return
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {}
    }
}
