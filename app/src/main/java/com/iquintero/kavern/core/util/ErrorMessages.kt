package com.iquintero.kavern.core.util

import android.content.Context
import com.iquintero.kavern.R
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** True when the failure means the server could not be reached at all (offline, DNS, timeout). */
fun Throwable.isConnectivityError(): Boolean =
    this is UnknownHostException ||
        this is ConnectException ||
        this is NoRouteToHostException ||
        this is SocketTimeoutException

/** Text for the UI: a short offline hint for connectivity failures, otherwise the exception message. */
fun Throwable.userMessage(context: Context): String? = if (isConnectivityError()) context.getString(R.string.error_offline_not_saved) else message
