package com.example.data.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SecurityManager(context: Context) {

    val rulesEngine: SecurityRulesEngine = SecurityRulesEngine(context)

    private val sharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "vvf_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for systems with keystore initialization edge cases
        context.getSharedPreferences("vvf_secure_prefs_fallback", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_PIN_HASH = "vvf_pin_hash"
        private const val KEY_PIN_SALT = "vvf_pin_salt"
        private const val KEY_BIOMETRIC_ENABLED = "vvf_biometric_enabled"
        private const val ITERATIONS = 12000
        private const val KEY_LENGTH = 256
    }

    /**
     * Checks if a security PIN is set in the device storage.
     */
    fun isPinSet(): Boolean {
        return sharedPreferences.contains(KEY_PIN_HASH) && sharedPreferences.contains(KEY_PIN_SALT)
    }

    /**
     * Hashes the user's PIN using PBKDF2 and stores both the hash and salt.
     */
    fun savePin(pin: String): Boolean {
        if (pin.length < 4) return false
        try {
            val salt = generateSalt()
            val hash = hashPin(pin, salt)
            
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            
            sharedPreferences.edit()
                .putString(KEY_PIN_HASH, hash)
                .putString(KEY_PIN_SALT, saltBase64)
                .apply()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Verifies if the provided PIN matches the stored PBKDF2 hash.
     * Enforces anti-brute-force lockout policies.
     */
    fun verifyPin(pin: String): Boolean {
        if (!isPinSet()) return false
        if (rulesEngine.isLockedOut()) return false
        try {
            val storedHash = sharedPreferences.getString(KEY_PIN_HASH, null) ?: return false
            val storedSaltBase64 = sharedPreferences.getString(KEY_PIN_SALT, null) ?: return false
            val salt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)
            
            val computedHash = hashPin(pin, salt)
            val isValid = storedHash == computedHash

            if (isValid) {
                rulesEngine.resetFailedAttempts()
            } else {
                rulesEngine.recordFailedAttempt()
            }

            return isValid
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Checks if biometric authentication is enabled.
     */
    fun isBiometricEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    /**
     * Enables or disables biometric authentication.
     */
    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    /**
     * Clears all credentials (useful for reset flows).
     */
    fun resetSecurity() {
        sharedPreferences.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_BIOMETRIC_ENABLED)
            .apply()
    }

    // Helper to generate cryptographically secure random salt
    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    // Helper to run PBKDF2 hashing
    private fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}
