package com.iquintero.kavern.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iquintero.kavern.R
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.network.ServerReachability

/** What the app currently knows about its link to the Kavita server. */
enum class ServerConnection {
    Connected,
    Checking,
    Unknown,
    OfflineMode,
    NoNetwork,
    ServerUnreachable,
    ;

    /** True when the server cannot be used, so only downloaded content is available. */
    val isUnavailable: Boolean
        get() = this == OfflineMode || this == NoNetwork || this == ServerUnreachable
}

@Composable
fun rememberServerConnection(): ServerConnection {
    val context = LocalContext.current
    val network by remember(context) { NetworkMonitor.getInstance(context).status }.collectAsState()
    val unreachable by ServerReachability.unreachable.collectAsState()
    val verified by ServerReachability.verified.collectAsState()
    val checking by ServerReachability.checking.collectAsState()
    return when {
        network.offlineMode -> ServerConnection.OfflineMode
        !network.isOnline -> ServerConnection.NoNetwork
        unreachable -> ServerConnection.ServerUnreachable
        verified -> ServerConnection.Connected
        checking -> ServerConnection.Checking
        else -> ServerConnection.Unknown
    }
}

/**
 * Cloud icon for top bars: done only once the server answered, pulsing while checking, plain when not
 * yet known, crossed out when the server cannot be used.
 */
@Composable
fun ServerConnectionIndicator(
    connection: ServerConnection,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "serverCheck").animateFloat(
        initialValue = PULSE_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "serverCheckAlpha",
    )
    val (icon, description) =
        when (connection) {
            ServerConnection.Connected -> Icons.Filled.CloudDone to stringResource(R.string.server_status_connected)
            ServerConnection.Checking -> Icons.Filled.Cloud to stringResource(R.string.server_status_checking)
            ServerConnection.Unknown -> Icons.Filled.Cloud to stringResource(R.string.server_status_unknown)
            ServerConnection.OfflineMode -> Icons.Filled.CloudOff to stringResource(R.string.server_status_offline_mode)
            ServerConnection.NoNetwork -> Icons.Filled.CloudOff to stringResource(R.string.server_status_no_network)
            ServerConnection.ServerUnreachable -> Icons.Filled.CloudOff to stringResource(R.string.server_status_unreachable)
        }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.alpha(if (connection == ServerConnection.Checking) pulse else 1f),
    )
}

private const val PULSE_MIN_ALPHA = 0.3f
private const val PULSE_MS = 700
