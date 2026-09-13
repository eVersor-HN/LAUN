package com.eversorhn.laun.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** How long a slider has to sit still before its value is committed to DataStore. Short enough
 *  that lifting the finger feels instant-saved, long enough to collapse a whole drag (dozens of
 *  value changes per second) into one write. */
private const val LIVE_SETTING_WRITE_DEBOUNCE_MS = 150L

/**
 * A slider-backed setting that reads instantly and persists lazily.
 *
 * Every slider used to fire a DataStore `edit` on every pixel of drag — and each `edit` is a full
 * atomic rewrite of the prefs file to disk. A one-second drag was 50+ file writes, all queued
 * behind each other, and for every setting except SIZE/COUNT the UI itself was reading the value
 * back out of that same queue, so the grid visibly lagged the finger.
 *
 * This keeps a local copy that the UI reads and the slider writes directly (zero latency), and
 * commits to DataStore only once the value has stopped changing for [LIVE_SETTING_WRITE_DEBOUNCE_MS].
 * While a local edit is pending, incoming persisted values are ignored (they're stale by
 * definition — our own write hasn't landed yet); once the write completes, the persisted stream
 * takes over again as the source of truth, so a change from anywhere else (RESET TO DEFAULTS, a
 * second setter) still flows through.
 *
 * Returns the current value and a setter. The setter is stable across recompositions.
 */
@Composable
fun <T> rememberLiveSetting(persisted: T, write: suspend (T) -> Unit): Pair<T, (T) -> Unit> {
    val state = remember { LiveSettingState(persisted) }
    val latestWrite by rememberUpdatedState(write)

    LaunchedEffect(persisted) {
        if (!state.dirty) state.value = persisted
    }
    LaunchedEffect(state.value, state.dirty) {
        if (!state.dirty) return@LaunchedEffect
        delay(LIVE_SETTING_WRITE_DEBOUNCE_MS)
        latestWrite(state.value)
        state.dirty = false
    }
    return state.value to state.setter
}

private class LiveSettingState<T>(initial: T) {
    var value by mutableStateOf(initial)
    var dirty by mutableStateOf(false)
    val setter: (T) -> Unit = { v ->
        if (v != value) {
            value = v
            dirty = true
        }
    }
}
