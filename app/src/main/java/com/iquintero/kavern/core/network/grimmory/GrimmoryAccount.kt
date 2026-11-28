package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.storage.AppConfig
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Who a saved Grimmory server is signed in as. The app keeps one secret per server, so a Grimmory account is kept in that
 * same field as `grimmory:<user>`, percent-encoded. That is all that is saved there: the sign-in itself is a token kept
 * apart, in [GrimmoryTokenStore], so that the password is never stored.
 *
 * `grimmory:<user>:<password>` is the older way, from before tokens: it can still be read, and signs in once, after which the
 * password is dropped. It is also how a password is handed to the app for a moment while a sign-in is checked.
 */
internal data class GrimmoryAccount(
    val username: String,
    /** Only present for an older save or a sign-in being checked; never in what is kept. */
    val password: String? = null,
) {
    fun encode(): String = PREFIX + URLEncoder.encode(username, "UTF-8") + (password?.let { ":" + URLEncoder.encode(it, "UTF-8") } ?: "")

    companion object {
        private const val PREFIX = "grimmory:"

        fun decode(secret: String): GrimmoryAccount? {
            if (!secret.startsWith(PREFIX)) return null
            val parts = secret.removePrefix(PREFIX).split(':')
            return when (parts.size) {
                1 -> GrimmoryAccount(URLDecoder.decode(parts[0], "UTF-8"))
                2 -> GrimmoryAccount(URLDecoder.decode(parts[0], "UTF-8"), URLDecoder.decode(parts[1], "UTF-8"))
                else -> null
            }.takeIf { it?.username?.isNotEmpty() == true }
        }
    }
}

internal val AppConfig.grimmory: GrimmoryAccount? get() = GrimmoryAccount.decode(apiKey)

/** True when the server saved in this config is a Grimmory one. */
val AppConfig.isGrimmory: Boolean get() = grimmory != null

/** What is saved for a signed-in Grimmory account: the name only. */
fun grimmorySecret(username: String): String = GrimmoryAccount(username).encode()

/** What is handed over, for a moment, to sign in with a password. It is not what gets saved. */
fun grimmoryPasswordSecret(
    username: String,
    password: String,
): String = GrimmoryAccount(username, password).encode()

/** The user name and, for an older save, the password inside a stored Grimmory secret, or null for anything else. */
fun String.grimmoryLogin(): Pair<String, String?>? = GrimmoryAccount.decode(this)?.let { it.username to it.password }
