package com.iquintero.kavern.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.iquintero.kavern.R
import com.iquintero.kavern.ui.theme.LocalEinkMode

/**
 * The app's "loading" mark. A spinner normally; on an e-ink screen a still hourglass, because a spinner redraws the
 * panel continuously and leaves ghost trails.
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    if (LocalEinkMode.current) {
        Icon(
            imageVector = Icons.Filled.HourglassEmpty,
            contentDescription = stringResource(R.string.general_loading),
            tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
            modifier = modifier,
        )
    } else if (color == Color.Unspecified) {
        CircularProgressIndicator(modifier = modifier)
    } else {
        CircularProgressIndicator(modifier = modifier, color = color)
    }
}
