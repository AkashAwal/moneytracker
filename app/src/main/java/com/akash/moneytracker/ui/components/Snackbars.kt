package com.akash.moneytracker.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long a message stays on screen. */
const val SNACKBAR_MILLIS = 5_000L

/**
 * Shows [message] and takes it away after [millis].
 *
 * Material keeps a snackbar that has an action (like "Undo") on screen until it's tapped, which left
 * "Transaction deleted" stuck over the tab bar. So it is shown as indefinite and dismissed by our own timer.
 */
suspend fun SnackbarHostState.showFor(
    message: String,
    actionLabel: String? = null,
    millis: Long = SNACKBAR_MILLIS,
): SnackbarResult = coroutineScope {
    val timer = launch {
        delay(millis)
        currentSnackbarData?.dismiss()
    }
    try {
        showSnackbar(message, actionLabel, duration = SnackbarDuration.Indefinite)
    } finally {
        timer.cancel()
    }
}

/** A snackbar host whose messages can also be flicked away sideways. */
@Composable
fun SwipeAwaySnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data -> SwipeAwaySnackbar(data) }
}

@Composable
private fun SwipeAwaySnackbar(data: SnackbarData) {
    // One swipe state per message, so a new message never starts out already swiped off.
    key(data) {
        val state = rememberSwipeToDismissBoxState()
        LaunchedEffect(state.currentValue) {
            if (state.currentValue != SwipeToDismissBoxValue.Settled) data.dismiss()
        }
        SwipeToDismissBox(state = state, backgroundContent = {}) {
            Snackbar(data)
        }
    }
}
