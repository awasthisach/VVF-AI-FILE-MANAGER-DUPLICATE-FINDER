package com.example.data.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MetadataEncryptionService(context: Context) {

    private val sharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "vvf_metadata_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for JVM testing/older android versions where KeyStore is missing
        context.getSharedPreferences("vvf_metadata_prefs_fallback", Context.MODE_PRIVATE)
    }

    private val secretKey: SecretKeySpec

    init {
        val keyBase64 = sharedPreferences.getString("metadata_aes_key", null)
        val keyBytes = if (keyBase64 != null) {
            try {
                Base64.decode(keyBase64, Base64.NO_WRAP)
            } catch (e: Exception) {
                generateNewKey()
            }
        } else {
            generateNewKey()
        }
        secretKey = SecretKeySpec(keyBytes, "AES")
    }

    private fun generateNewKey(): ByteArray {
        val random = SecureRandom()
        val key = ByteArray(32) // AES-256
        random.nextBytes(key)
        val keyBase64 = Base64.encodeToString(key, Base64.NO_WRAP)
        sharedPreferences.edit().putString("metadata_aes_key", keyBase64).apply()
        return key
    }

    /**
     * Encrypts the provided string using AES-GCM.
     * Returns a base64 string combining the 12-byte IV and ciphertext.
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12) // GCM standard IV size
            SecureRandom().nextBytes(iv)
            val parameterSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            plaintext
        }
    }

    /**
     * Decrypts a base64 encoded string containing combined IV and ciphertext.
     */
    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < 12) return encryptedBase64

            val iv = ByteArray(12)
            val ciphertext = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, ciphertext, 0, ciphertext.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val parameterSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            encryptedBase64
        }
    }
}
