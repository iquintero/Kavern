package com.iquintero.kavern.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ReadMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iquintero.kavern.R
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.network.grimmory.isGrimmory
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.domain.repository.AuthRepository
import com.iquintero.kavern.ui.settings.screens.SettingsAboutScreen
import com.iquintero.kavern.ui.settings.screens.SettingsAdvancedScreen
import com.iquintero.kavern.ui.settings.screens.SettingsAppearanceScreen
import com.iquintero.kavern.ui.settings.screens.SettingsDownloadScreen
import com.iquintero.kavern.ui.settings.screens.SettingsGeneralScreen
import com.iquintero.kavern.ui.settings.screens.SettingsPlaceholderScreen
import com.iquintero.kavern.ui.settings.screens.SettingsReaderScreen
import com.iquintero.kavern.ui.settings.screens.SettingsServerScreen
import com.iquintero.kavern.ui.settings.screens.SettingsStatsScreen

private sealed class SettingsCategory(
    @StringRes val titleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    object General : SettingsCategory(R.string.settings_item_general, Icons.Filled.ReadMore)

    object Appearance : SettingsCategory(R.string.settings_item_appearance, Icons.Filled.Palette)

    object Reader : SettingsCategory(R.string.settings_item_reader, Icons.Filled.Language)

    object Downloads : SettingsCategory(R.string.settings_item_downloads, Icons.Filled.Storage)

    object Security : SettingsCategory(R.string.settings_item_security, Icons.Filled.Security)

    object Server : SettingsCategory(R.string.settings_item_server, Icons.Filled.Tune)

    object Advanced : SettingsCategory(R.string.settings_item_advanced, Icons.Filled.Lock)

    object Stats : SettingsCategory(R.string.settings_item_stats, Icons.Filled.Sync)

    object About : SettingsCategory(R.string.settings_item_about, Icons.Filled.Info)
}

@Suppress("UnusedPrivateProperty")
@Composable
fun SettingsScreen(
    appPreferences: AppPreferences,
    authRepository: AuthRepository,
    cacheManager: CacheManager,
    onOpenAbout: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var selected by remember { mutableStateOf<SettingsCategory?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isGrimmory = appPreferences.sessionConfig?.isGrimmory == true
    val categories =
        listOf(
            SettingsCategory.General,
            SettingsCategory.Appearance,
            SettingsCategory.Reader,
            SettingsCategory.Downloads,
            SettingsCategory.Security,
            SettingsCategory.Server,
            SettingsCategory.Advanced,
            SettingsCategory.Stats,
            SettingsCategory.About,
            // Kavita's reading statistics are not something a Grimmory server has.
        ).filter { !(isGrimmory && it == SettingsCategory.Stats) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (selected != null) {
            BackHandler { selected = null }
        }
        when (val cat = selected) {
            null -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
                        }
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding =
                            androidx.compose.foundation.layout
                                .PaddingValues(16.dp),
                    ) {
                        categories.forEach { item ->
                            item {
                                SettingRow(
                                    icon = item.icon,
                                    title = stringResource(item.titleRes),
                                    onClick = {
                                        if (item is SettingsCategory.About) {
                                            onOpenAbout()
                                        } else {
                                            selected = item
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            SettingsCategory.Server ->
                SettingsServerScreen(
                    appPreferences = appPreferences,
                    authRepository = authRepository,
                    onBack = { selected = null },
                )
            SettingsCategory.General ->
                SettingsGeneralScreen(
                    appPreferences = appPreferences,
                    onBack = { selected = null },
                )
            SettingsCategory.Appearance ->
                SettingsAppearanceScreen(
                    appPreferences = appPreferences,
                    onBack = { selected = null },
                )
            SettingsCategory.Reader ->
                SettingsReaderScreen(
                    appPreferences = appPreferences,
                    onBack = { selected = null },
                )
            SettingsCategory.Downloads -> SettingsDownloadScreen(appPreferences) { selected = null }
            SettingsCategory.Security -> SettingsPlaceholderScreen(R.string.settings_item_security) { selected = null }
            SettingsCategory.Advanced ->
                SettingsAdvancedScreen(
                    appPreferences = appPreferences,
                    cacheManager = cacheManager,
                    onBack = { selected = null },
                )
            SettingsCategory.Stats ->
                SettingsStatsScreen(
                    appPreferences = appPreferences,
                    onBack = { selected = null },
                )
            SettingsCategory.About -> SettingsAboutScreen { selected = null }
        }
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
