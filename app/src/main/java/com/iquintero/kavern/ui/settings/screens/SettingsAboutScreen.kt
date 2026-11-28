package com.iquintero.kavern.ui.settings.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iquintero.kavern.R
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsAboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var showLicenses by remember { mutableStateOf(false) }
    val libraries by produceLibraries(R.raw.aboutlibraries)
    val versionName =
        remember {
            runCatching {
                val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
                pkg.versionName ?: pkg.longVersionCode.toString()
            }.getOrDefault("--")
        }
    val buildTime =
        remember {
            runCatching {
                val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                formatter.format(Date(pkg.lastUpdateTime))
            }.getOrDefault("--")
        }
    val kavitaTarget = "nightly-0.9.1.20"
    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
    val scrollState = rememberScrollState()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
            }
            Icon(
                painter = painterResource(id = R.drawable.kavern_logo),
                contentDescription = "Kavern logo",
                modifier = Modifier.size(32.dp),
                tint = Color.Unspecified,
            )
            Text(stringResource(R.string.settings_about_title), style = MaterialTheme.typography.headlineSmall)
        }
        Text(stringResource(R.string.settings_about_version) + ": $versionName")
        Text(stringResource(R.string.settings_about_build_time) + ": $buildTime")
        Text(stringResource(R.string.settings_about_kavita_version) + ": $kavitaTarget")
        Text(stringResource(R.string.settings_about_grimmory_version) + ": $GRIMMORY_TESTED")

        Text(stringResource(R.string.settings_about_label_whats_new), style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_changelog_title)) },
            supportingContent = { Text(stringResource(R.string.settings_about_changelog_desc)) },
            leadingContent = { Icon(Icons.Filled.Update, contentDescription = null, modifier = Modifier.size(24.dp)) },
            modifier =
                Modifier
                    .clickable { openUrl("https://github.com/iquintero/Inkita/blob/master/CHANGELOG.md") }
                    .padding(vertical = 2.dp),
        )

        Text(stringResource(R.string.settings_about_label_community), style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_website)) },
            leadingContent = { Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(24.dp)) },
            modifier =
                Modifier
                    .clickable { openUrl("https://www.kavitareader.com") }
                    .padding(vertical = 2.dp),
        )
        ListItem(
            headlineContent = { Text("Kavita Discord") },
            leadingContent = {
                Icon(
                    painterResource(id = R.drawable.ic_discord),
                    contentDescription = "Discord",
                    modifier = Modifier.size(24.dp),
                )
            },
            modifier =
                Modifier
                    .clickable { openUrl("https://discord.gg/b52wT37kt7") }
                    .padding(vertical = 2.dp),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_grimmory_website)) },
            leadingContent = { Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(24.dp)) },
            modifier =
                Modifier
                    .clickable { openUrl("https://grimmory.org") }
                    .padding(vertical = 2.dp),
        )
        ListItem(
            headlineContent = { Text("Grimmory Discord") },
            leadingContent = {
                Icon(
                    painterResource(id = R.drawable.ic_discord),
                    contentDescription = "Discord",
                    modifier = Modifier.size(24.dp),
                )
            },
            modifier =
                Modifier
                    .clickable { openUrl("https://discord.gg/9YJ7HB4n8T") }
                    .padding(vertical = 2.dp),
        )
        ListItem(
            headlineContent = { Text("GitHub") },
            leadingContent = {
                Icon(
                    painterResource(id = R.drawable.ic_github),
                    contentDescription = "GitHub",
                    modifier = Modifier.size(24.dp),
                )
            },
            modifier =
                Modifier
                    .clickable { openUrl("https://github.com/iquintero/Inkita") }
                    .padding(vertical = 2.dp),
        )

        Text(stringResource(R.string.settings_about_label_contribute), style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_osl)) },
            supportingContent = { Text(stringResource(R.string.settings_about_osl_3rd)) },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(24.dp)) },
            modifier =
                Modifier
                    .clickable { showLicenses = true }
                    .padding(vertical = 2.dp),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_license)) },
            supportingContent = { Text("Apache-2.0") },
            leadingContent = { Icon(Icons.Filled.Update, contentDescription = null, modifier = Modifier.size(24.dp)) },
            modifier =
                Modifier
                    .clickable { openUrl("https://www.apache.org/licenses/LICENSE-2.0") }
                    .padding(vertical = 2.dp),
        )
    }

    if (showLicenses) {
        BackHandler { showLicenses = false }
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            LibrariesContainer(
                libraries = libraries,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val GRIMMORY_TESTED = "3.5.1"
