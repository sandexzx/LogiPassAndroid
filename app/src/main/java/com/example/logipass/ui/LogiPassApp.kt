package com.example.logipass.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logipass.AppContainer
import com.example.logipass.session.AuthState
import com.example.logipass.ui.components.ToastState
import com.example.logipass.ui.navigation.NavHost
import com.example.logipass.ui.navigation.Route
import com.example.logipass.ui.navigation.rememberNavigator
import com.example.logipass.ui.screens.lock.LockScreen
import com.example.logipass.ui.screens.settings.SettingsScreen
import com.example.logipass.ui.screens.setup.SetupScreen
import com.example.logipass.ui.screens.vault.VaultScreen
import com.example.logipass.ui.theme.LogiPassTheme
import com.example.logipass.ui.theme.MistBackground

private enum class Phase { Loading, Setup, Locked, Unlocked }

private val AuthState.phase: Phase
    get() = when (this) {
        AuthState.Loading -> Phase.Loading
        is AuthState.NeedsSetup -> Phase.Setup
        is AuthState.Locked -> Phase.Locked
        AuthState.Unlocked -> Phase.Unlocked
    }

/**
 * Root of the UI. The auth state picks the screen; inside an unlocked session a small
 * back stack holds the vault and settings. Locking drops the session screens entirely, so
 * no decrypted data stays composed behind the lock screen.
 */
@Composable
fun LogiPassApp(container: AppContainer) {
    LogiPassTheme {
        val state by container.auth.state.collectAsStateWithLifecycle()
        // One toast queue for the whole app: a message posted right before a screen change
        // (biometric enrollment after setup, a wipe) still shows on the next screen.
        val toast = remember { ToastState() }
        // A lock the user asked for should not immediately pop the fingerprint prompt.
        var lockedByUser by remember { mutableStateOf(false) }

        LaunchedEffect(state.phase) {
            if (state.phase == Phase.Unlocked) lockedByUser = false
        }

        AnimatedContent(
            targetState = state,
            contentKey = { it.phase },
            transitionSpec = {
                (fadeIn(tween(320, delayMillis = 60)) + scaleIn(tween(320, delayMillis = 60), initialScale = 0.97f))
                    .togetherWith(fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.02f))
            },
            label = "authPhase",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                AuthState.Loading -> MistBackground(Modifier.fillMaxSize())
                is AuthState.NeedsSetup -> SetupScreen(container, current.legacyServiceCount, toast)
                is AuthState.Locked -> LockScreen(container, current, toast, autoPrompt = !lockedByUser)
                AuthState.Unlocked -> UnlockedSession(container, toast, onLock = {
                    lockedByUser = true
                    container.auth.lock()
                })
            }
        }
    }
}

@Composable
private fun UnlockedSession(container: AppContainer, toast: ToastState, onLock: () -> Unit) {
    val navigator = rememberNavigator()
    NavHost(navigator) { route ->
        when (route) {
            Route.Vault -> VaultScreen(
                container = container,
                toast = toast,
                onOpenSettings = { navigator.push(Route.Settings) },
                onLock = onLock,
            )
            Route.Settings -> SettingsScreen(
                container = container,
                toast = toast,
                onBack = { navigator.pop() },
                onLock = onLock,
            )
        }
    }
}
