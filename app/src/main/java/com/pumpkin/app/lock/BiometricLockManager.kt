package com.pumpkin.app.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.pumpkin.app.R

/** PRD 4.2: BiometricPrompt primary, PIN fallback handled separately via [PinManager]. */
class BiometricLockManager(private val activity: FragmentActivity) {

    fun canUseBiometrics(): Boolean {
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.lock_biometric_prompt_subtitle))
            .setNegativeButtonText(activity.getString(R.string.lock_use_pin))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
