package com.javzzap.laxbrowser.platform

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Asks for the screen lock (fingerprint, face, PIN, pattern, or password) before showing,
 * copying, or exporting saved passwords, and to unlock private tabs.
 */
object DeviceAuth {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** False when the device has no screen lock, so passwords cannot be protected. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Shows the prompt; does nothing when the activity cannot show it right now (for example while it is being restored). */
    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
        if (activity.supportFragmentManager.isStateSaved) return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        runCatching {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setAllowedAuthenticators(AUTHENTICATORS)
                    .build(),
            )
        }
    }
}
