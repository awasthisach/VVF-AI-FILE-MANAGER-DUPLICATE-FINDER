package com.example.data.security

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    /**
     * Checks whether biometric hardware and enrolled credentials (fingerprint/face) are available.
     */
    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Prompts the user with a Biometric verification dialog using androidx.biometric.BiometricPrompt.
     * Executes [onSuccess] if authenticated or if biometrics are not configured/hardware unavailable.
     */
    fun authenticateForSensitiveAccess(
        context: Context,
        title: String = "संवेदनशील फ़ाइल प्रमाणीकरण",
        subtitle: String = "संवेदनशील जानकारी देखने के लिए बायोमेट्रिक प्रमाणीकरण आवश्यक है",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val activity = context as? FragmentActivity
        if (activity == null) {
            // Fallback if context cannot be cast to FragmentActivity
            onSuccess()
            return
        }

        if (!isBiometricAvailable(activity)) {
            // Hardware or enrollment unavailable, allow fallback with notice
            Toast.makeText(activity, "बायोमेट्रिक प्रमाणीकरण बायपास किया गया (हार्डवेयर अनुपलब्ध)", Toast.LENGTH_SHORT).show()
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(activity, "प्रमाणीकरण सफल!", Toast.LENGTH_SHORT).show()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(activity, "प्रमाणीकरण त्रुटि: $errString", Toast.LENGTH_SHORT).show()
                        onError(errString.toString())
                    } else {
                        onError("प्रमाणीकरण रद्द किया गया")
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(activity, "प्रमाणीकरण विफल! पुनः प्रयास करें", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("रद्द करें")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
