package com.iquintero.kavern.core.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Minimal wrapper around EncryptedSharedPreferences for sensitive values (API key).
 * Falls back to empty strings on errors to avoid crashes.
 */
class SecureStorage(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val masterKey =
        MasterKey
            .Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    private val prefs =
        EncryptedSharedPreferences.create(
            appContext,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun getApiKey(reference: String?): String = safeGet(keyFor(KEY_API_KEY, reference))

    fun getImageApiKey(reference: String?): String = safeGet(keyFor(KEY_IMAGE_API_KEY, reference))

    fun saveKavitaKeys(
        reference: String,
        apiKey: String,
        imageApiKey: String,
    ) {
        check(
            prefs
                .edit()
                .putString(keyFor(KEY_API_KEY, reference), apiKey)
                .putString(keyFor(KEY_IMAGE_API_KEY, reference), imageApiKey)
                .commit(),
        ) { "Unable to save Kavita credentials" }
    }

    fun removeKavitaKeys(reference: String?) {
        prefs
            .edit()
            .remove(keyFor(KEY_API_KEY, reference))
            .remove(keyFor(KEY_IMAGE_API_KEY, reference))
            .apply()
    }

    private fun keyFor(
        key: String,
        reference: String?,
    ): String = if (reference == null) key else "$key:$reference"

    fun setApiKey(apiKey: String) {
        prefs
            .edit()
            .putString(KEY_API_KEY, apiKey)
            .apply()
    }

    /** What is kept of a Grimmory sign-in (its tokens, as one piece of text), under [key]; empty when there is none. */
    fun getGrimmoryTokens(key: String): String = safeGet("grimmory_tokens:$key")

    fun saveGrimmoryTokens(
        key: String,
        tokens: String,
    ) {
        check(prefs.edit().putString("grimmory_tokens:$key", tokens).commit()) { "Unable to save the Grimmory sign-in" }
    }

    fun removeGrimmoryTokens(key: String) {
        prefs.edit().remove("grimmory_tokens:$key").commit()
    }

    fun clearLegacyTokens() {
        prefs
            .edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    private fun safeGet(key: String): String = runCatching { prefs.getString(key, "") ?: "" }.getOrDefault("")

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_IMAGE_API_KEY = "image_api_key"
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
