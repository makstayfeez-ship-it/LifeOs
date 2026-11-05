package com.lifeos.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class AuthUser(
    val uid: String,
    val name: String,
    val email: String,
    /** true — аккаунт в Firebase (данные можно сохранить в облаке), false — локальный режим. */
    val cloud: Boolean,
)

/**
 * Регистрация и вход. Если в проекте есть google-services.json, используется Firebase Auth.
 * Если нет — работает локальный аккаунт на устройстве, чтобы приложение можно было запустить
 * и проверить без настройки облака.
 */
class AuthRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("lifeos_local_auth", Context.MODE_PRIVATE)

    val cloudAvailable: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    private val _user = MutableStateFlow(currentFromStorage())
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    private fun currentFromStorage(): AuthUser? {
        if (cloudAvailable) {
            val u = FirebaseAuth.getInstance().currentUser ?: return null
            return AuthUser(u.uid, u.displayName.orEmpty(), u.email.orEmpty(), cloud = true)
        }
        if (!prefs.getBoolean("signed_in", false)) return null
        return AuthUser(
            uid = "local",
            name = prefs.getString("name", "").orEmpty(),
            email = prefs.getString("email", "").orEmpty(),
            cloud = false,
        )
    }

    suspend fun register(name: String, email: String, password: String): Result<Unit> = runCatching {
        if (cloudAvailable) {
            val result = FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).await()
            result.user
                ?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
                ?.await()
        } else {
            val salt = UUID.randomUUID().toString()
            prefs.edit()
                .putString("name", name)
                .putString("email", email)
                .putString("salt", salt)
                .putString("hash", hash(salt, password))
                .putBoolean("signed_in", true)
                .apply()
        }
        _user.value = currentFromStorage()
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        if (cloudAvailable) {
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
        } else {
            val salt = prefs.getString("salt", null)
            val ok = salt != null &&
                prefs.getString("email", null).equals(email, ignoreCase = true) &&
                prefs.getString("hash", null) == hash(salt, password)
            if (!ok) error("Неверный email или пароль")
            prefs.edit().putBoolean("signed_in", true).apply()
        }
        _user.value = currentFromStorage()
    }

    suspend fun resetPassword(email: String): Result<Unit> = runCatching {
        check(cloudAvailable) { "Сброс пароля доступен только с подключённым облаком" }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
    }

    fun signOut() {
        if (cloudAvailable) FirebaseAuth.getInstance().signOut()
        else prefs.edit().putBoolean("signed_in", false).apply()
        _user.value = null
    }

    private fun hash(salt: String, password: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + password).toByteArray())
            .joinToString("") { "%02x".format(it) }
}

fun Throwable.toRussian(): String = when (this) {
    is FirebaseAuthWeakPasswordException -> "Слишком простой пароль: нужно минимум 6 символов"
    is FirebaseAuthUserCollisionException -> "Этот email уже зарегистрирован"
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException -> "Неверный email или пароль"
    is FirebaseNetworkException -> "Нет соединения с интернетом"
    else -> message ?: "Что-то пошло не так"
}
