package com.lifeos.app.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN-код хранится только в виде PBKDF2-хэша с солью; сам PIN нигде не записывается. */
object PinLock {
    private const val ITERATIONS = 100_000
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(pin: String, saltHex: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), saltHex.fromHex(), ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return key.toHex()
    }

    fun verify(pin: String, saltHex: String, expectedHash: String): Boolean {
        if (saltHex.isBlank() || expectedHash.isBlank()) return false
        val actual = hash(pin, saltHex)
        return MessageDigest.isEqual(actual.toByteArray(), expectedHash.toByteArray())
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

object Biometrics {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** Есть ли на телефоне настроенный отпечаток или распознавание лица. */
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    /** Показывает системное окно отпечатка/лица. [onSuccess] вызывается только после успешного распознавания. */
    fun prompt(context: Context, onSuccess: () -> Unit) {
        val activity = context.findFragmentActivity() ?: return
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("LifeOS")
            .setSubtitle("Подтвердите, что это вы")
            .setAllowedAuthenticators(AUTH)
            .setNegativeButtonText("Ввести PIN-код")
            .build()
        runCatching { prompt.authenticate(info) }
    }

    private fun Context.findFragmentActivity(): FragmentActivity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }
}
