package com.example.logipass.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.example.logipass.AppContainer
import com.example.logipass.security.BiometricOutcome
import com.example.logipass.ui.components.Icons
import com.example.logipass.ui.components.ToastState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The hosting activity; BiometricPrompt needs a FragmentActivity. */
@Composable
fun rememberFragmentActivity(): FragmentActivity {
    val context = LocalContext.current
    return remember(context) { context.findFragmentActivity() }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> error("LogiPass UI must be hosted in a FragmentActivity")
}

/**
 * Runs a vault write in the app scope, so leaving the screen never cancels a save. The
 * repository may have already published the change when a write fails, so the user is told
 * plainly that it was not saved.
 */
fun AppContainer.launchWrite(toast: ToastState, block: suspend () -> Unit): Job = scope.launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        toast.error("Не удалось сохранить изменения")
    }
}

enum class EnrollOutcome { Enabled, Cancelled, Failed }

/** Asks for a fingerprint and stores the biometric-wrapped vault key. Requires an unlocked vault. */
suspend fun AppContainer.enrollBiometric(activity: FragmentActivity): EnrollOutcome = try {
    val cipher = auth.biometricEnrollCipher()
    when (val outcome = biometric.authenticate(activity, cipher, "Вход по отпечатку", "Приложите палец, чтобы включить", "Отмена")) {
        is BiometricOutcome.Success -> {
            auth.enableBiometric(outcome.cipher)
            EnrollOutcome.Enabled
        }
        BiometricOutcome.Cancelled -> EnrollOutcome.Cancelled
        is BiometricOutcome.Error -> EnrollOutcome.Failed
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    EnrollOutcome.Failed
}

fun ToastState.showEnrollOutcome(outcome: EnrollOutcome) = when (outcome) {
    EnrollOutcome.Enabled -> show("Вход по отпечатку включён", Icons.Fingerprint)
    EnrollOutcome.Cancelled -> Unit
    EnrollOutcome.Failed -> error("Не удалось включить вход по отпечатку")
}

/** Text for BiometricPrompt errors worth telling the user about; null for the rest. */
fun biometricErrorText(code: Int): String? = when (code) {
    BiometricPrompt.ERROR_LOCKOUT -> "Слишком много попыток. Отпечаток временно недоступен"
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Отпечаток заблокирован. Разблокируйте телефон, затем попробуйте снова"
    BiometricPrompt.ERROR_NO_BIOMETRICS -> "На телефоне нет сохранённых отпечатков"
    BiometricPrompt.ERROR_TIMEOUT -> null
    else -> "Отпечаток не распознан. Введите пароль"
}
