package com.waivera.waivera

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.navigation.AppShellViewModel
import com.waivera.waivera.startup.DemoLaunch
import com.waivera.waivera.ui.CustomTabsUriHandler
import com.waivera.waivera.ui.ShellActions
import com.waivera.waivera.ui.WaiveraApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val shell: AppShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before injection: repositories are chosen once per process, so a changed debug demo flag
        // restarts the process. Release builds have no demo mode and this is always false.
        if (DemoLaunch.restartIfChanged(this, intent)) {
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity already restored its navigation; replaying the launch link would undo it.
        if (savedInstanceState == null) handleLink(intent)
        holdFirstFrameUntilSettingsLoad()

        setContent {
            val state by shell.uiState.collectAsStateWithLifecycle()
            val darkTheme = when (state.settings.theme) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> shell.onResumedChanged(true)
                        Lifecycle.Event.ON_PAUSE -> shell.onResumedChanged(false)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            val actions = remember {
                ShellActions(
                    select = shell::select,
                    push = shell::push,
                    back = shell::back,
                    openFromAccountMenu = shell::openFromAccountMenu,
                    onPrimerFinished = shell::onPrimerFinished,
                    signOut = shell::signOut,
                )
            }
            WaiveraTheme(darkTheme = darkTheme, hapticsEnabled = state.settings.hapticsEnabled) {
                val toolbarColor = WaiveraTheme.colors.surface.toArgb()
                val uriHandler = remember(toolbarColor) { CustomTabsUriHandler(this, toolbarColor) }
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    WaiveraApp(state = state, actions = actions)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    /**
     * Holds the first frame (the system splash stays up) until the stored settings are read, so a
     * Dark preference never flashes light (08 §7). Capped, so a slow store never blocks start-up.
     */
    private fun holdFirstFrameUntilSettingsLoad() {
        val content = findViewById<View>(android.R.id.content)
        val deadline = SystemClock.uptimeMillis() + FIRST_FRAME_HOLD_MAX_MILLIS
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = shell.uiState.value.settingsLoaded || SystemClock.uptimeMillis() >= deadline
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }

    private fun handleLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        shell.onDeepLink(intent.dataString)
    }

    private companion object {
        const val FIRST_FRAME_HOLD_MAX_MILLIS = 1_000L
    }
}
