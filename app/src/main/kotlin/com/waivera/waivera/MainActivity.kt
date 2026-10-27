package com.waivera.waivera

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.navigation.AppShellViewModel
import com.waivera.waivera.startup.DemoLaunch
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
                    onPrimerFinished = shell::onPrimerFinished,
                    retryBootstrap = shell::retryBootstrap,
                    signOut = shell::signOut,
                )
            }
            WaiveraTheme(darkTheme = darkTheme, hapticsEnabled = state.settings.hapticsEnabled) {
                WaiveraApp(state = state, actions = actions)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        shell.onDeepLink(intent.dataString)
    }
}
