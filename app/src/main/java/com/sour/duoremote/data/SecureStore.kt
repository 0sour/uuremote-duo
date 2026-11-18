package com.sour.duoremote.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Encrypted local storage for signing material and session credentials.
 * Never log values from this class.
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "duo_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun hasSigningKey(): Boolean = prefs.contains(KEY_SIGNING)

    fun saveSigningKey(key: ByteArray) {
        prefs.edit().putString(KEY_SIGNING, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
    }

    fun signingKey(): ByteArray? {
        val encoded = prefs.getString(KEY_SIGNING, null) ?: return null
        return runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull()
    }

    fun clearSigningKey() {
        prefs.edit().remove(KEY_SIGNING).apply()
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveUserId(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun userId(): String? = prefs.getString(KEY_USER_ID, null)

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    fun deviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun getOrCreateClientId(): String {
        prefs.getString(KEY_CLIENT_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_CLIENT_ID, id).apply()
        return id
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_DEVICE_ID)
            .apply()
    }

    private companion object {
        const val KEY_SIGNING = "signing_key_v1"
        const val KEY_TOKEN = "token_v1"
        const val KEY_USER_ID = "user_id_v1"
        const val KEY_DEVICE_ID = "device_id_v1"
        const val KEY_CLIENT_ID = "client_id_v1"
    }
}
