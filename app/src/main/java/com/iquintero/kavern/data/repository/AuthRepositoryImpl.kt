package com.iquintero.kavern.data.repository

import com.iquintero.kavern.core.network.KavitaApiFactory
import com.iquintero.kavern.core.network.grimmory.GrimmoryCredentials
import com.iquintero.kavern.core.network.grimmory.GrimmoryHttpException
import com.iquintero.kavern.core.network.grimmory.GrimmoryServers
import com.iquintero.kavern.core.network.grimmory.grimmory
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.data.api.dto.UserDto
import com.iquintero.kavern.domain.repository.AuthRepository
import com.iquintero.kavern.domain.repository.KavitaConnectionException
import com.iquintero.kavern.domain.repository.KavitaConnectionException.Problem
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Response
import java.io.EOFException
import java.io.IOException
import java.security.GeneralSecurityException
import javax.net.ssl.SSLException

class AuthRepositoryImpl internal constructor(
    private val saveConfig: suspend (AppConfig) -> Unit,
) : AuthRepository {
    constructor(appPreferences: AppPreferences) : this(appPreferences::updateKavitaConfig)

    override suspend fun configure(config: AppConfig): AppConfig {
        val accountId = verifyConnection(config)
        val verified = config.copy(userId = accountId)
        saveConfig(verified)
        return verified
    }

    private suspend fun verifyConnection(config: AppConfig): Int {
        val url =
            config.serverUrl.toHttpUrlOrNull()?.takeIf {
                it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null &&
                    (config.caCertificate == null || it.isHttps)
            } ?: throw KavitaConnectionException(Problem.InvalidUrl)
        if (config.apiKey.isBlank() || config.apiKey.any { it !in ' '..'~' }) {
            throw KavitaConnectionException(Problem.RejectedKey)
        }
        val checked = config.copy(serverUrl = url.toString())
        config.grimmory?.let { return withContext(Dispatchers.IO) { verifyGrimmory(checked, it) } }
        return requireAccountId(requestAccount(checked))
    }

    @Suppress("ThrowsCount")
    private fun verifyGrimmory(
        config: AppConfig,
        credentials: GrimmoryCredentials,
    ): Int {
        try {
            GrimmoryServers.verify(config, credentials)
        } catch (e: IOException) {
            throw grimmoryFailure(e)
        } catch (e: GeneralSecurityException) {
            throw grimmoryFailure(e)
        } catch (e: JsonDataException) {
            throw grimmoryFailure(e)
        }
        // Grimmory does not number its users in what the app can read, so the login name stands in for an account id.
        return (credentials.username.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)
    }

    private suspend fun requestAccount(config: AppConfig): Response<UserDto> {
        // Validate the address entered by the user without forwarding its API key to a redirect target.
        return try {
            KavitaApiFactory.createAuthenticated(config, followRedirects = false).getAccount()
        } catch (e: Exception) {
            val problem =
                when (e) {
                    is SSLException, is GeneralSecurityException -> Problem.Tls
                    is JsonDataException, is JsonEncodingException, is EOFException -> Problem.UnexpectedResponse
                    is IOException -> Problem.Unreachable
                    else -> throw e
                }
            throw KavitaConnectionException(problem)
        }
    }

    private fun requireAccountId(response: Response<UserDto>): Int {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            val status = response.code()
            val problem = if (status == 401 || status == 403) Problem.RejectedKey else Problem.UnexpectedResponse
            throw KavitaConnectionException(problem, status)
        }
        return response.body()?.id?.takeIf { it > 0 } ?: throw KavitaConnectionException(Problem.UnexpectedResponse)
    }

    private fun grimmoryFailure(error: Exception): KavitaConnectionException = KavitaConnectionException(grimmoryProblem(error), (error as? GrimmoryHttpException)?.code)

    private fun grimmoryProblem(error: Exception): Problem =
        when {
            error is GrimmoryHttpException && error.code in REJECTED -> Problem.RejectedKey
            error is GrimmoryHttpException && error.code == TOO_MANY_REQUESTS -> Problem.RateLimited
            error is GrimmoryHttpException || error is JsonDataException -> Problem.UnexpectedResponse
            error is SSLException || error is GeneralSecurityException -> Problem.Tls
            else -> Problem.Unreachable
        }

    private companion object {
        val REJECTED = setOf(400, 401, 403)
        const val TOO_MANY_REQUESTS = 429
    }
}
