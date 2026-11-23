package com.iquintero.kavern.ui.settings.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iquintero.kavern.R
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.storage.AppTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsAppearanceScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val selectedTheme by appPreferences.appThemeFlow.collectAsState(initial = AppTheme.System)
    val dynamicColor by appPreferences.dynamicColorFlow.collectAsState(initial = true)
    val einkMode by appPreferences.einkModeFlow.collectAsState(initial = false)
    val themeOptions =
        listOf(
            AppTheme.System to R.string.settings_appearance_by_system,
            AppTheme.Light to R.string.settings_appearance_light,
            AppTheme.Dark to R.string.settings_appearance_dark,
        )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
        }
        Text(stringResource(R.string.settings_appearance_title), style = MaterialTheme.typography.headlineSmall)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            themeOptions.forEachIndexed { index, (theme, label) ->
                SegmentedButton(
                    selected = selectedTheme == theme,
                    onClick = { scope.launch { appPreferences.setAppTheme(theme) } },
                    shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size),
                ) {
                    Text(stringResource(label))
                }
            }
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_appearance_eink)) },
            supportingContent = { Text(stringResource(R.string.settings_appearance_eink_subtitle)) },
            trailingContent = {
                Switch(
                    checked = einkMode,
                    onCheckedChange = { scope.launch { appPreferences.setEinkMode(it) } },
                )
            },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_appearance_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.settings_appearance_dynamic_color_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { scope.launch { appPreferences.setDynamicColor(it) } },
                    )
                },
            )
        }
    }
}
