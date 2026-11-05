package com.akash.moneytracker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.akash.moneytracker.lock.AppLock
import com.akash.moneytracker.lock.LockScreen
import com.akash.moneytracker.ui.AppRoot
import com.akash.moneytracker.ui.theme.MoneyTrackerTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as MoneyTrackerApp
                MainViewModel(app.repository, app.settingsStore)
            }
        }
    }

    /** null while the setting is still being read, so the app's content never flashes before the lock. */
    private var locked by mutableStateOf<Boolean?>(null)
    private var lockMessage by mutableStateOf<String?>(null)
    private var lockEnabled = false

    /** 0 = follow the phone, 1 = light, 2 = dark (see Settings.themeMode). */
    private var themeMode by mutableIntStateOf(0)
    private var stoppedAt = 0L

    /** Bumped each time the widget, tile or shortcut asks for the add sheet to open. */
    private var quickAddSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        protectAgainstOverlays()

        val settings = (application as MoneyTrackerApp).settingsStore.settings
        lifecycleScope.launch {
            val first = settings.first()
            lockEnabled = first.lockEnabled
            themeMode = first.themeMode
            locked = lockEnabled
            settings.map { it.themeMode }.distinctUntilChanged().collect { themeMode = it }
        }
        lifecycleScope.launch {
            val first = settings.first()
            lockEnabled = first.lockEnabled
            settings.map { it.lockEnabled }.distinctUntilChanged().collect { enabled ->
                // Screenshots and screen sharing are deliberately allowed, lock or no lock.
                lockEnabled = enabled
            }
        }

        setContent {
            val dark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            // Status- and navigation-bar icons must contrast with the app's theme, which may differ from the phone's.
            DisposableEffect(dark) {
                val bar = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
                onDispose {}
            }
            MoneyTrackerTheme(darkTheme = dark) {
                when (locked) {
                    null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    true -> LockScreen(message = lockMessage, onUnlock = ::promptUnlock)
                    false -> AppRoot(viewModel, quickAddSignal)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // A short grace period so the share sheet or file picker doesn't re-lock the app on the way back.
        if (lockEnabled && locked == false && SystemClock.elapsedRealtime() - stoppedAt > LOCK_AFTER_MS) {
            locked = true
        }
    }

    /**
     * Tap-jacking defence: another app can't draw a look-alike button over ours (for example over "Replace"
     * in the restore dialog) and have our window act on the touch.
     */
    private fun protectAgainstOverlays() {
        window.decorView.filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setHideOverlayWindows(true)
    }

    private fun promptUnlock() {
        AppLock.prompt(
            activity = this,
            title = "Unlock Money Tracker",
            onSuccess = {
                lockMessage = null
                locked = false
            },
            onFailure = { lockMessage = it },
        )
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_QUICK_ADD -> {
                quickAddSignal++
                intent.action = null // so rotating the phone doesn't open the sheet a second time
            }
            Intent.ACTION_SEND -> {
                // Text shared from another app is untrusted: it can only become a suggestion the user must confirm.
                if (intent.type == "text/plain") {
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(viewModel::importText)
                }
                intent.action = null
            }
        }
    }

    companion object {
        /** Sent by the widget, the quick-settings tile, the launcher shortcut and the reminder notification. */
        const val ACTION_QUICK_ADD = "com.akash.moneytracker.QUICK_ADD"
        /** Short on purpose: a stranger who picks up the phone shouldn't get long with an unlocked app. */
        private const val LOCK_AFTER_MS = 30_000L
    }
}
