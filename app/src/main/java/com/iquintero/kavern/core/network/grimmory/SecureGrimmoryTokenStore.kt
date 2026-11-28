package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.storage.SecureStorage
import okio.ByteString.Companion.encodeUtf8

/** Keeps the tokens of one Grimmory account in the encrypted storage, apart from the saved configuration. */
internal class SecureGrimmoryTokenStore(
    private val storage: SecureStorage,
    serverUrl: String,
    username: String,
    private val onDropPassword: () -> Unit,
) : GrimmoryTokenStore {
    private val key = "${serverUrl.trimEnd('/')}\n$username".encodeUtf8().sha256().hex()

    override fun load(): GrimmoryTokens? = storage.getGrimmoryTokens(key).takeIf { it.isNotEmpty() }?.let { GrimmoryTokens.decode(it) }

    override fun save(tokens: GrimmoryTokens) = storage.saveGrimmoryTokens(key, tokens.encode())

    override fun clear() = storage.removeGrimmoryTokens(key)

    override fun dropPassword() = onDropPassword()
}
