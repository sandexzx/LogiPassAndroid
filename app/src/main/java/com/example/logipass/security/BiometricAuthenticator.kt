package com.example.logipass.security

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import javax.crypto.Cipher

sealed interface BiometricOutcome {
    data class Success(val cipher: Cipher) : BiometricOutcome
    data object Cancelled : BiometricOutcome
    data class Error(val code: Int) : BiometricOutcome
}
class BiometricAuthenticator {
    suspend fun authenticate(activity: FragmentActivity, cipher: Cipher, title: String, subtitle: String?, negativeText: String): BiometricOutcome =
        suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(result.cryptoObject?.cipher?.let { BiometricOutcome.Success(it) }
                        ?: BiometricOutcome.Error(BiometricPrompt.ERROR_UNABLE_TO_PROCESS))
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(when (errorCode) {
                        BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> BiometricOutcome.Cancelled
                        else -> BiometricOutcome.Error(errorCode)
                    })
                }
            })
            continuation.invokeOnCancellation { activity.runOnUiThread { prompt.cancelAuthentication() } }
            val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText(negativeText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            if (subtitle != null) info.setSubtitle(subtitle)
            activity.runOnUiThread { if (continuation.isActive) prompt.authenticate(info.build(), BiometricPrompt.CryptoObject(cipher)) }
        }
}
