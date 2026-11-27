package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.storage.AppConfig
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Grimmory signs in with a username and password, where Kavita uses a single API key. The app stores one secret per
 * server, so a Grimmory login is kept in that same field as `grimmory:<user>:<password>`, both percent-encoded. That
 * keeps it in the encrypted storage and in the profile handling without a second set of credentials.
 */
internal data class GrimmoryCredentials(
    val username: String,
    val password: String,
) {
    fun encode(): String = PREFIX + URLEncoder.encode(username, "UTF-8") + ":" + URLEncoder.encode(password, "UTF-8")

    companion object {
        private const val PREFIX = "grimmory:"

        fun decode(secret: String): GrimmoryCredentials? {
            if (!secret.startsWith(PREFIX)) return null
            val parts = secret.removePrefix(PREFIX).split(':')
            if (parts.size != 2) return null
            return GrimmoryCredentials(URLDecoder.decode(parts[0], "UTF-8"), URLDecoder.decode(parts[1], "UTF-8"))
        }
    }
}

internal val AppConfig.grimmory: GrimmoryCredentials? get() = GrimmoryCredentials.decode(apiKey)

/** True when the server saved in this config is a Grimmory one. */
val AppConfig.isGrimmory: Boolean get() = grimmory != null

/** What to store as the secret of an AppConfig for a Grimmory login. */
fun grimmorySecret(
    username: String,
    password: String,
): String = GrimmoryCredentials(username, password).encode()

/** The user name and password inside a stored Grimmory secret, or null for anything else. */
fun String.grimmoryLogin(): Pair<String, String>? = GrimmoryCredentials.decode(this)?.let { it.username to it.password }
