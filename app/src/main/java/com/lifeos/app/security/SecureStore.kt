package com.lifeos.app.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Зашифрованное хранилище файлов для раздела «Документы».
 * AES-256-GCM, ключ создаётся в Android Keystore и не покидает телефон. Файлы лежат во внутренней
 * памяти приложения только в зашифрованном виде; для просмотра на время создаётся временная копия в кэше.
 */
object SecureStore {
    private const val ALIAS = "lifeos_documents"
    private const val DIR = "docs"
    private const val VIEW_DIR = "view"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /** Читает файл по [uri], шифрует и сохраняет. Возвращает имя зашифрованного файла и тип. */
    fun import(context: Context, uri: Uri): Pair<String, String?> {
        val mime = context.contentResolver.getType(uri)
        val name = UUID.randomUUID().toString() + ".enc"
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val input = context.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
        input.use { encrypt(it, File(dir, name).outputStream()) }
        return name to mime
    }

    private fun encrypt(input: InputStream, out: OutputStream) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        out.use { raw ->
            raw.write(iv.size)
            raw.write(iv)
            CipherOutputStream(raw, cipher).use { input.copyTo(it) }
        }
    }

    fun exists(context: Context, name: String?): Boolean =
        !name.isNullOrBlank() && File(File(context.filesDir, DIR), name).exists()

    fun delete(context: Context, name: String?) {
        if (!name.isNullOrBlank()) File(File(context.filesDir, DIR), name).delete()
    }

    /** Расшифровывает во временную копию и возвращает Uri для просмотрщика. */
    fun openForView(context: Context, name: String, title: String, mime: String?): Uri {
        val src = File(File(context.filesDir, DIR), name)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val dir = File(context.cacheDir, VIEW_DIR).apply { mkdirs() }
        val safe = title.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(40).ifBlank { "document" }
        val out = File(dir, "$safe.$ext")
        src.inputStream().use { raw ->
            val ivSize = raw.read()
            val iv = ByteArray(ivSize)
            check(raw.read(iv) == ivSize) { "Файл повреждён" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            CipherInputStream(raw, cipher).use { plain -> out.outputStream().use { plain.copyTo(it) } }
        }
        return FileProvider.getUriForFile(context, context.packageName + ".files", out)
    }

    /** Удаляет временные расшифрованные копии. */
    fun clearViewCache(context: Context) {
        File(context.cacheDir, VIEW_DIR).listFiles()?.forEach { it.delete() }
    }
}
