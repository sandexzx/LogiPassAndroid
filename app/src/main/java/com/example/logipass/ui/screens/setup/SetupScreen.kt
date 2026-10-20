package com.example.logipass.ui.screens.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.logipass.AppContainer
import com.example.logipass.session.AuthManager
import com.example.logipass.session.BiometricAvailability
import com.example.logipass.ui.components.AppIcon
import com.example.logipass.ui.components.AppScaffold
import com.example.logipass.ui.components.CardRow
import com.example.logipass.ui.components.Glass
import com.example.logipass.ui.components.GroupFooter
import com.example.logipass.ui.components.IconBadge
import com.example.logipass.ui.components.Icons
import com.example.logipass.ui.components.LocalCardBackdrop
import com.example.logipass.ui.components.PasswordInput
import com.example.logipass.ui.components.PrimaryButton
import com.example.logipass.ui.components.SurfaceCard
import com.example.logipass.ui.components.ToastHost
import com.example.logipass.ui.components.ToastState
import com.example.logipass.ui.components.Toggle
import com.example.logipass.ui.enrollBiometric
import com.example.logipass.ui.format.servicesCount
import com.example.logipass.ui.rememberFragmentActivity
import com.example.logipass.ui.showEnrollOutcome
import com.example.logipass.ui.theme.AppType
import com.example.logipass.ui.theme.Style
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** First run (or after a wipe): choose the master password, optionally turn on fingerprint unlock. */
@Composable
fun SetupScreen(container: AppContainer, legacyServiceCount: Int?, toast: ToastState) {
    val style = Style
    val activity = rememberFragmentActivity()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val availability = remember { container.auth.biometricAvailability() }

    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var useBiometric by rememberSaveable { mutableStateOf(availability == BiometricAvailability.Available) }
    var busy by remember { mutableStateOf(false) }
    val confirmFocus = remember { FocusRequester() }

    val longEnough = password.length >= AuthManager.MIN_PASSWORD_LENGTH
    val matches = confirm.isNotEmpty() && confirm == password
    val valid = longEnough && matches

    fun submit() {
        if (!valid || busy) return
        busy = true
        focus.clearFocus()
        val chosen = password
        val enroll = useBiometric && availability == BiometricAvailability.Available
        scope.launch {
            try {
                if (!container.auth.setup(chosen)) {
                    busy = false
                    return@launch
                }
                // The vault is unlocked now and this screen is leaving; enrollment outlives it.
                if (enroll) {
                    container.scope.launch { toast.showEnrollOutcome(container.enrollBiometric(activity)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                busy = false
                toast.error("Не удалось создать хранилище. Попробуйте ещё раз")
            }
        }
    }

    AppScaffold(
        overlay = { ToastHost(toast) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            HeroIcon()
            Spacer(Modifier.height(20.dp))
            Text(
                "Мастер-пароль",
                style = AppType.largeTitle,
                color = style.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Он шифрует все ваши пароли и нигде не хранится. Если забыть его, восстановить данные не получится.",
                style = AppType.subheadline,
                color = style.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(24.dp))

            if (legacyServiceCount != null && legacyServiceCount > 0) {
                SurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(Icons.Shield, style.positive)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Найдено ${servicesCount(legacyServiceCount)}",
                                style = AppType.headline,
                                color = style.textPrimary,
                            )
                            Text(
                                "Данные из прошлой версии перенесутся и будут зашифрованы новым паролем.",
                                style = AppType.footnote,
                                color = style.textSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            SurfaceCard(contentPadding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PasswordInput(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Новый пароль",
                        enabled = !busy,
                        imeAction = ImeAction.Next,
                        onImeAction = { confirmFocus.requestFocus() },
                    )
                    StrengthMeter(password)
                    PasswordInput(
                        value = confirm,
                        onValueChange = { confirm = it },
                        placeholder = "Повторите пароль",
                        enabled = !busy,
                        revealable = false,
                        imeAction = ImeAction.Done,
                        onImeAction = ::submit,
                        focusRequester = confirmFocus,
                    )
                    Column(
                        Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Requirement("Не короче ${AuthManager.MIN_PASSWORD_LENGTH} символов", longEnough)
                        Requirement("Пароли совпадают", matches)
                    }
                }
            }

            when (availability) {
                BiometricAvailability.Available -> {
                    Spacer(Modifier.height(16.dp))
                    SurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                        CardRow(
                            title = "Вход по отпечатку",
                            leading = { IconBadge(Icons.Fingerprint, style.accent) },
                            trailing = { Toggle(useBiometric, { useBiometric = it }, enabled = !busy) },
                            onClick = { if (!busy) useBiometric = !useBiometric },
                        )
                    }
                    GroupFooter("Пароль всё равно понадобится после перезагрузки и смены отпечатков.")
                }
                BiometricAvailability.NoneEnrolled -> {
                    Spacer(Modifier.height(4.dp))
                    GroupFooter("Добавьте отпечаток в настройках телефона, и вход по нему можно будет включить позже.")
                }
                BiometricAvailability.Unavailable -> Unit
            }

            Spacer(Modifier.height(28.dp))
            PrimaryButton(
                text = "Создать хранилище",
                onClick = ::submit,
                enabled = valid,
                loading = busy,
            )
            AnimatedVisibility(busy) {
                Text(
                    "Шифруем… это займёт пару секунд",
                    style = AppType.footnote,
                    color = style.textSecondary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** App mark: a glass disc (a square capsule) refracting the background blobs. */
@Composable
internal fun HeroIcon(icon: ImageVector = Icons.Shield) {
    val style = Style
    Glass(
        backdrop = LocalCardBackdrop.current,
        style = style.cardGlass.copy(surface = Color.White.copy(alpha = 0.55f)),
        modifier = Modifier.size(88.dp),
    ) {
        AppIcon(icon, style.accent, size = 40.dp)
    }
}

@Composable
private fun Requirement(text: String, met: Boolean) {
    val style = Style
    val color by animateColorAsState(if (met) style.positive else style.textGhost, label = "requirement")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (met) 1f else 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            if (met) AppIcon(Icons.Check, Color.White, size = 12.dp)
        }
        Text(text, style = AppType.footnote, color = if (met) style.textPrimary else style.textSecondary)
    }
}

/**
 * Rough strength hint from length and character variety. Deliberately simple: it nudges
 * towards longer passwords, it does not pretend to estimate entropy.
 */
@Composable
private fun StrengthMeter(password: String) {
    val style = Style
    val score = passwordScore(password)
    val (label, color) = when (score) {
        0 -> "" to style.textGhost
        1 -> "Слабый" to style.destructive
        2 -> "Средний" to style.warning
        3 -> "Хороший" to style.positive
        else -> "Надёжный" to style.positive
    }
    AnimatedVisibility(password.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { index ->
                    val segment by animateColorAsState(
                        if (index < score) color else style.insetFill,
                        label = "strengthSegment",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(segment),
                    )
                }
            }
            Text(label, style = AppType.caption, color = color)
        }
    }
}

/** 0 for empty, then 1..4; long passphrases score well even without symbols. */
private fun passwordScore(password: String): Int {
    if (password.isEmpty()) return 0
    val classes = listOf(
        password.any(Char::isLowerCase),
        password.any(Char::isUpperCase),
        password.any(Char::isDigit),
        password.any { !it.isLetterOrDigit() },
    ).count { it }
    val length = password.length
    return when {
        length < AuthManager.MIN_PASSWORD_LENGTH -> 1
        length >= 20 || (length >= 14 && classes == 4) -> 4
        length >= 16 || (length >= 12 && classes >= 3) -> 3
        length >= 12 || classes >= 2 -> 2
        else -> 1
    }
}
