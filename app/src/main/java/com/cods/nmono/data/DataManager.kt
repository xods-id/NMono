package com.cods.nmono.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

val Context.dataStore by preferencesDataStore(name = "settings")

object PreferencesKeys {
    val THEME = stringPreferencesKey("theme_mode")
    val PASSWORD_HASH = stringPreferencesKey("password_hash")
    val PASSWORD_SALT = stringPreferencesKey("password_salt")
    val SORT_TYPE = stringPreferencesKey("sort_type")
    val CUSTOM_ORDER = stringPreferencesKey("custom_order")
    val TAG_HISTORY = stringPreferencesKey("tag_history")
    val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")
    val SHOW_EXIT_DIALOG = booleanPreferencesKey("show_exit_dialog")
}

class DataManager(private val context: Context) {
    private val gson = Gson()
    private val notesFile = File(context.filesDir, "notes.json")

    suspend fun saveNotes(notes: List<Note>, password: String?) = withContext(Dispatchers.IO) {
        val json = gson.toJson(notes)
        if (password != null) {
            val encrypted = encrypt(json, password)
            if (encrypted.isNotEmpty()) notesFile.writeText(encrypted)
        } else {
            notesFile.writeText(json)
        }
    }

    suspend fun loadNotes(password: String?): List<Note> = withContext(Dispatchers.IO) {
        if (!notesFile.exists()) return@withContext emptyList<Note>()
        val content = try { notesFile.readText() } catch (e: Exception) { "" }
        if (content.isBlank()) return@withContext emptyList<Note>()
        
        return@withContext try {
            val trimmed = content.trim()
            if (password != null && !trimmed.startsWith("[") && !trimmed.startsWith("{")) {
                val decrypted = decrypt(content, password)
                if (decrypted.isBlank()) return@withContext emptyList()
                gson.fromJson(decrypted, object : TypeToken<List<Note>>(){}.type) ?: emptyList()
            } else if (trimmed.startsWith("[")) {
                gson.fromJson(content, object : TypeToken<List<Note>>(){}.type) ?: emptyList()
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun isEncrypted(): Boolean = withContext(Dispatchers.IO) {
        if (!notesFile.exists()) return@withContext false
        val content = try { notesFile.readText() } catch (e: Exception) { "" }
        val trimmed = content.trim()
        content.isNotBlank() && !trimmed.startsWith("[") && !trimmed.startsWith("{")
    }

    fun encrypt(data: String, password: String): String {
        return try {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val combined = salt + iv + encrypted
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }

    fun decrypt(encoded: String, password: String): String {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 28) return ""
            val salt = combined.sliceArray(0 until 16)
            val iv = combined.sliceArray(16 until 28)
            val encrypted = combined.sliceArray(28 until combined.size)
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) { "" }
    }

    fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 100000, 256)
        return factory.generateSecret(spec).encoded
    }
}

fun hashPassword(password: String, salt: ByteArray): String {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(password.toCharArray(), salt, 150000, 256)
    val hash = factory.generateSecret(spec).encoded
    return Base64.encodeToString(hash, Base64.NO_WRAP)
}

fun generateSalt(): ByteArray {
    val salt = ByteArray(16)
    SecureRandom().nextBytes(salt)
    return salt
}
