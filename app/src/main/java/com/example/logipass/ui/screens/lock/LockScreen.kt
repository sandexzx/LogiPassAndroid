package com.example.logipass.ui.screens.lock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.logipass.AppContainer
import com.example.logipass.security.BiometricOutcome
import com.example.logipass.session.AuthState
import com.example.logipass.session.BiometricCipher
import com.example.logipass.session.UnlockResult
import com.example.logipass.ui.biometricErrorText
import com.example.logipass.ui.components.ActionSheetContent
import com.example.logipass.ui.components.AppIcon
import com.example.logipass.ui.components.AppScaffold
import com.example.logipass.ui.components.GlassButton
import com.example.logipass.ui.components.Icons
import com.example.logipass.ui.components.PasswordInput
import com.example.logipass.ui.components.PrimaryButton
import com.example.logipass.ui.components.SheetAction
import com.example.logipass.ui.components.SheetHost
import com.example.logipass.ui.components.SurfaceCard
import com.example.logipass.ui.components.TextLink
import com.example.logipass.ui.components.ToastHost
import com.example.logipass.ui.components.ToastState
import com.example.logipass.ui.components.shake
import com.example.logipass.ui.format.formatCountdown
import com.example.logipass.ui.rememberFragmentActivity
import com.example.logipass.ui.screens.setup.HeroIcon
import com.example.logipass.ui.theme.AppType
import com.example.logipass.ui.theme.Style
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class LockSheet { Forgot, ConfirmWipe }

/**
 * Locked vault: master password, or fingerprint when enabled. The fingerprint prompt opens
 * by itself once each time the app comes back to the foreground, unless the user just
 * locked by hand ([autoPrompt] = false).
 */
@Composable
fun LockScreen(container: AppContainer, state: AuthState.Locked, toast: ToastState, autoPrompt: Boolean) {
    val style = Style
    val auth = container.auth
    val activity = rememberFragmentActivity()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var shakes by remember { mutableIntStateOf(0) }
    var bioInvalidated by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<LockSheet?>(null) }
    val fieldFocus = remember { FocusRequester() }

    // Lockout countdown, ticking while it lasts.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lockedOutUntil = state.lockedOutUntil
    LaunchedEffect(lockedOutUntil) {
        while (lockedOutUntil != null) {
            now = System.currentTimeMillis()
            if (now >= lockedOutUntil) break
            delay(250)
        }
    }
    val lockedOut = lockedOutUntil != null && now < lockedOutUntil

    fun focusField() {
        try {
            fieldFocus.requestFocus()
        } catch (_: IllegalStateException) {
            // Field not laid out yet (screen still animating in).
        }
    }

    suspend fun biometricUnlock() {
        message = null
        try {
            when (val ready = auth.biometricUnlockCipher()) {
                is BiometricCipher.Ready -> {
                    when (val outcome = container.biometric.authenticate(activity, ready.cipher, "Разблокировать LogiPass", null, "Ввести пароль")) {
                        is BiometricOutcome.Success -> {
                            if (!auth.unlockWithBiometric(outcome.cipher)) message = "Не получилось. Введите пароль"
                        }
                        BiometricOutcome.Cancelled -> focusField()
                        is BiometricOutcome.Error -> {
                            message = biometricErrorText(outcome.code)
                            focusField()
                        }
                    }
                }
                BiometricCipher.Invalidated -> {
                    bioInvalidated = true
                    focusField()
                }
                BiometricCipher.NotEnabled -> focusField()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            message = "Отпечаток сейчас недоступен. Введите пароль"
            focusField()
        }
    }

    fun submit() {
        if (password.isEmpty() || busy || lockedOut) return
        busy = true
        message = null
        val attempt = password
        scope.launch {
            try {
                when (val result = auth.unlockWithPassword(attempt)) {
                    UnlockResult.Success -> Unit
                    is UnlockResult.WrongPassword -> {
                        password = ""
                        shakes++
                        message = if (result.lockedOutUntil == null) "Неверный пароль" else null
                        focusField()
                    }
                    is UnlockResult.LockedOut -> {
                        password = ""
                        message = null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "Не удалось открыть хранилище"
            } finally {
                busy = false
            }
        }
    }

    // Auto-prompt: once per trip to the foreground. Leaving the app (ON_STOP) re-arms it.
    var prompted by remember { mutableStateOf(!autoPrompt) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) prompted = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(prompted, state.biometricEnabled) {
        if (prompted) return@LaunchedEffect
        lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        delay(300) // let the screen settle first
        prompted = true
        // Separate job: flipping `prompted` restarts this effect, which must not cancel what follows.
        val biometric = state.biometricEnabled
        scope.launch { if (biometric) biometricUnlock() else focusField() }
    }

    AppScaffold(
        overlay = {
            ToastHost(toast)
            SheetHost(sheet, onDismiss = { sheet = null }) { current ->
                when (current) {
                    LockSheet.Forgot -> ActionSheetContent(
                        title = "Забыли мастер-пароль?",
                        message = "Восстановить его нельзя: пароли зашифрованы только им. " +
                            "Можно стереть хранилище и начать заново. Если есть резервная копия, " +
                            "её можно будет импортировать.",
                        actions = listOf(
                            SheetAction("Стереть хранилище", Icons.Trash, destructive = true) {
                                sheet = LockSheet.ConfirmWipe
                            },
                        ),
                        onDismiss = { sheet = null },
                    )
                    LockSheet.ConfirmWipe -> ActionSheetContent(
                        title = "Стереть все данные?",
                        message = "Все сервисы и пароли будут удалены с этого телефона. Отменить это нельзя.",
                        actions = listOf(
                            SheetAction("Стереть всё", Icons.Trash, destructive = true) {
                                container.scope.launch {
                                    try {
                                        auth.wipeAll()
                                        toast.show("Хранилище стёрто", Icons.Trash)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        toast.error("Не удалось стереть данные")
                                    }
                                }
                            },
                        ),
                        onDismiss = { sheet = null },
                    )
                }
            }
        },
    ) { padding ->
        // Centered when it fits, scrollable when the keyboard leaves too little room.
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                HeroIcon(Icons.Lock)
                Spacer(Modifier.height(20.dp))
                Text("LogiPass", style = AppType.largeTitle, color = style.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Введите мастер-пароль",
                    style = AppType.subheadline,
                    color = style.textSecondary,
                )
                Spacer(Modifier.height(28.dp))

                SurfaceCard(
                    modifier = Modifier.shake(shakes),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    PasswordInput(
                        value = password,
                        onValueChange = {
                            password = it
                            if (message != null) message = null
                        },
                        placeholder = if (lockedOut) "Подождите…" else "Мастер-пароль",
                        enabled = !busy && !lockedOut,
                        imeAction = ImeAction.Go,
                        onImeAction = ::submit,
                        focusRequester = fieldFocus,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Открыть",
                        onClick = ::submit,
                        enabled = password.isNotEmpty() && !lockedOut,
                        loading = busy,
                    )
                }

                StatusLine(
                    text = when {
                        lockedOut -> "Слишком много попыток. Повторите через ${formatCountdown(lockedOutUntil!! - now)}"
                        else -> message
                    },
                    warning = lockedOut,
                )

                AnimatedVisibility(bioInvalidated) {
                    SurfaceCard(
                        modifier = Modifier.padding(top = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcon(Icons.Info, style.accentText, size = 20.dp)
                            Text(
                                "Отпечатки на телефоне изменились, поэтому вход по отпечатку выключен. " +
                                    "Войдите паролем и включите его снова в настройках.",
                                style = AppType.footnote,
                                color = style.textPrimary,
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(24.dp))
                if (state.biometricEnabled) {
                    GlassButton(onClick = { scope.launch { biometricUnlock() } }, height = 52.dp) {
                        AppIcon(Icons.Fingerprint, style.accentText, size = 24.dp)
                        Text("Войти по отпечатку", style = AppType.headline, color = style.accentText)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextLink("Забыли пароль?", onClick = { sheet = LockSheet.Forgot }, color = style.textSecondary)
            }
        }
    }
}

/** One line under the password card for errors and the lockout countdown; keeps its height. */
@Composable
private fun StatusLine(text: String?, warning: Boolean) {
    val style = Style
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it?.substringBefore("через") },
        label = "lockStatus",
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(40.dp),
    ) { line ->
        if (line != null) {
            Text(
                line,
                style = if (warning) AppType.footnote.merge(AppType.numeric) else AppType.footnote,
                color = if (warning) style.warning else style.destructive,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
