package com.iquintero.kavern.ui.grimmory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.iquintero.kavern.R
import com.iquintero.kavern.core.network.grimmory.GrimmoryHttpException
import com.iquintero.kavern.core.network.grimmory.GrimmoryServers
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.sync.ProgressSyncWorker
import com.iquintero.kavern.core.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Asks for the password when Grimmory no longer accepts the saved sign-in: after a month away, or a changed password. Reading
 * what is on the device carries on without it, and what was read since is sent once the sign-in is back.
 */
@Composable
internal fun GrimmorySignInPrompt(
    config: AppConfig,
    appPreferences: AppPreferences,
) {
    val username by GrimmoryServers.signInRequired.collectAsState()
    var later by remember { mutableStateOf(false) }
    val name = username
    if (name == null || later) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = { later = true },
        title = { Text(stringResource(R.string.grimmory_sign_in_title)) },
        text = {
            Column {
                Text(stringResource(R.string.grimmory_sign_in_body, name))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.settings_grimmory_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotEmpty(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val outcome = runCatchingCancellable { withContext(Dispatchers.IO) { GrimmoryServers.signIn(config, name, password) } }
                        busy = false
                        outcome
                            .onSuccess { ProgressSyncWorker.enqueue(context, appPreferences.profile) }
                            .onFailure { error = it.messageRes() }
                    }
                },
            ) { Text(stringResource(R.string.grimmory_sign_in_confirm)) }
        },
        dismissButton = { TextButton(onClick = { later = true }) { Text(stringResource(R.string.grimmory_sign_in_later)) } },
    )
}

private fun Throwable.messageRes(): Int =
    when {
        this is GrimmoryHttpException && code == TOO_MANY_REQUESTS -> R.string.settings_server_rate_limited
        this is GrimmoryHttpException && code in REFUSED -> R.string.grimmory_sign_in_wrong
        this is IOException -> R.string.settings_kavita_unreachable
        else -> R.string.settings_kavita_unexpected_response
    }

private const val TOO_MANY_REQUESTS = 429
private val REFUSED = setOf(400, 401, 403)
