package com.iquintero.kavern

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.Window
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.disk.DiskCache
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.network.forKavita
import com.iquintero.kavern.core.network.guardApiKey
import com.iquintero.kavern.core.startup.StartupManager
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.storage.AppTheme
import com.iquintero.kavern.data.repository.ComicPageFallback
import com.iquintero.kavern.domain.repository.AuthRepository
import com.iquintero.kavern.domain.repository.CollectionsRepository
import com.iquintero.kavern.domain.repository.LibraryRepository
import com.iquintero.kavern.domain.repository.SeriesRepository
import com.iquintero.kavern.ui.common.PageKeyEvents
import com.iquintero.kavern.ui.download.DownloadQueueScreen
import com.iquintero.kavern.ui.download.DownloadQueueViewModel
import com.iquintero.kavern.ui.download.DownloadQueueViewModelFactory
import com.iquintero.kavern.ui.history.HistoryScreen
import com.iquintero.kavern.ui.home.HomeScreen
import com.iquintero.kavern.ui.library.LibraryV2Screen
import com.iquintero.kavern.ui.navigation.MainScreen
import com.iquintero.kavern.ui.reader.model.ReaderReturn
import com.iquintero.kavern.ui.reader.screen.ReaderKeyEvents
import com.iquintero.kavern.ui.reader.screen.ReaderScreen
import com.iquintero.kavern.ui.seriesdetail.SeriesDetailScreenV2
import com.iquintero.kavern.ui.settings.SettingsScreen
import com.iquintero.kavern.ui.theme.KavernTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    // Page buttons (and optionally the volume keys) turn pages while a book is open instead of changing the volume.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = ReaderKeyEvents.handle(event) || PageKeyEvents.handle(event) || super.dispatchKeyEvent(event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appPreferences = StartupManager.init(applicationContext)
        // Apply saved locale before composing UI so strings use correct language on first draw.
        val storedLanguage = runBlocking { appPreferences.appLanguageFlow.first() }
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val localeTag = appLocales.toLanguageTags()
        val initialLanguage =
            when {
                localeTag.isNotBlank() -> localeTag
                storedLanguage.isNotBlank() -> storedLanguage
                else -> "system"
            }
        if (storedLanguage != initialLanguage) {
            runBlocking { appPreferences.setAppLanguage(initialLanguage) }
        }
        applyLocale(initialLanguage)

        setContent {
            val session by appPreferences.activeSessionFlow.collectAsState(initial = null)
            val current = session ?: return@setContent
            key(current) {
                val components = remember { StartupManager.components(AppPreferences(applicationContext, current)) }
                KavernApp(
                    initialLanguage = initialLanguage,
                    appPreferences = components.preferences,
                    libraryRepository = components.libraryRepository,
                    authRepository = components.authRepository,
                    seriesRepository = components.seriesRepository,
                    collectionsRepository = components.collectionsRepository,
                    readingListRepository = components.readingListRepository,
                    personRepository = components.personRepository,
                    readerRepository = components.readerRepository,
                    cacheManager = components.cacheManager,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-schedule one-off jobs (e.g., progress sync) when app returns to foreground.
        StartupManager.onResume(applicationContext)
    }
}

private const val IMPORTANT_INFO_HEADER =
    "✅ Image API key was added — please fill it in Kavita Settings."

private val IMPORTANT_INFO_ADDED = emptyList<String>()

private val IMPORTANT_INFO_CHANGED = emptyList<String>()

private val IMPORTANT_INFO_FIXED =
    listOf(
        "Fixed database upgrades from older versions.",
        "Preserved newer reading progress during sync.",
        "Fixed download pause, resume, retry, and recovery after interruption.",
        "Kept local data separate for each Kavita server and account.",
        "Retried reading progress uploads after server errors.",
        "Fixed moving downloads and deleting downloaded files.",
        "Fixed update checks and numbered beta version comparisons.",
        "Secured EPUB rendering while keeping existing downloads readable offline.",
    )

private const val FORCE_SHOW_IMPORTANT_INFO = false

/**
 * The version of the "important info" notes above. The dialog shows once to anyone who has not seen this version, so
 * raise it only when the notes change. It used to follow the app's version code, which would show the same old
 * notes again after every release.
 */
private const val IMPORTANT_INFO_VERSION = 11

private fun applyLocale(languageTag: String) {
    val locales =
        if (languageTag.isBlank() || languageTag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
    if (AppCompatDelegate.getApplicationLocales() != locales) {
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

@Composable
private fun InfoSection(
    title: String,
    items: List<String>,
) {
    if (items.isEmpty()) return

    Spacer(modifier = Modifier.size(10.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )
    Spacer(modifier = Modifier.size(6.dp))
    items.forEach { item ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("•")
            Text(
                text = item,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
    }
}

private const val SCREEN_FADE_MS = 700

/** Windows at least this wide (tablets, unfolded foldables) get a navigation rail instead of a bar. */
private const val RAIL_MIN_WIDTH_DP = 600
private const val SERVER_PROBE_CONNECT_TIMEOUT_SECONDS = 4L
private const val SERVER_PROBE_TIMEOUT_SECONDS = 6L
private const val SERVER_PROBE_INTERVAL_MS = 30_000L
private const val COVER_CACHE_DIR = "image_cache"
private const val COVER_CACHE_BYTES = 64L * 1024 * 1024

@Suppress("UnusedPrivateProperty")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KavernApp(
    initialLanguage: String,
    appPreferences: AppPreferences,
    libraryRepository: LibraryRepository,
    authRepository: AuthRepository,
    seriesRepository: SeriesRepository,
    collectionsRepository: CollectionsRepository,
    readingListRepository: com.iquintero.kavern.domain.repository.ReadingListRepository,
    personRepository: com.iquintero.kavern.domain.repository.PersonRepository,
    readerRepository: com.iquintero.kavern.domain.repository.ReaderRepository,
    cacheManager: CacheManager,
) {
    val einkMode by appPreferences.einkModeFlow.collectAsState(initial = false)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifPromptShown by appPreferences.notificationsPromptShownFlow.collectAsState(initial = false)
    val notificationsEnabled =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    val showNotifDialog = remember { mutableStateOf(false) }
    val lastImportantInfoVersion by appPreferences.importantInfoVersionFlow.collectAsState(initial = -1)
    val showImportantInfoDialog = remember { mutableStateOf(false) }
    val appLanguage by appPreferences.appLanguageFlow.collectAsState(initial = initialLanguage)
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Regardless of result, don't ask again.
            scope.launch { appPreferences.setNotificationsPromptShown(true) }
            showNotifDialog.value = false
        }

    LaunchedEffect(notifPromptShown, notificationsEnabled) {
        if (!notifPromptShown && !notificationsEnabled) {
            showNotifDialog.value = true
        } else if (!notifPromptShown && notificationsEnabled) {
            appPreferences.setNotificationsPromptShown(true)
        }
    }

    LaunchedEffect(lastImportantInfoVersion) {
        if (FORCE_SHOW_IMPORTANT_INFO) {
            showImportantInfoDialog.value = true
            return@LaunchedEffect
        }
        if (lastImportantInfoVersion < 0) return@LaunchedEffect
        showImportantInfoDialog.value = IMPORTANT_INFO_VERSION > lastImportantInfoVersion
    }

    LaunchedEffect(appLanguage) {
        applyLocale(appLanguage)
    }

    val appTheme by appPreferences.appThemeFlow.collectAsState(initial = AppTheme.System)
    val darkTheme =
        when (appTheme) {
            AppTheme.System -> isSystemInDarkTheme()
            AppTheme.Light -> false
            AppTheme.Dark -> true
        }
    val dynamicColor by appPreferences.dynamicColorFlow.collectAsState(initial = true)
    val maxThumbnailsParallel by appPreferences.maxThumbnailsParallelFlow.collectAsState(initial = 4)
    val view = LocalView.current

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val mainRoutes = MainScreen.items.map { it.route }
    val isMainRoute = MainScreen.items.any { item -> currentRoute?.startsWith(item.route) == true }
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )
    val networkStatus by NetworkMonitor.getInstance(context, appPreferences).status.collectAsState()

    // The phone can have internet while a home-only server is out of reach, so ask the server directly
    // whenever the connection changes and then keep checking while the app is open.
    LaunchedEffect(config.serverUrl, config.caCertificate, networkStatus.isOnlineAllowed, networkStatus.connectionType) {
        if (config.serverUrl.isBlank() || !networkStatus.isOnlineAllowed) return@LaunchedEffect
        val calls =
            runCatching {
                OkHttpClient
                    .Builder()
                    // The connect timeout must be shorter than the call timeout, otherwise the call
                    // timeout fires first and the failure is not recognised as "cannot connect".
                    .connectTimeout(SERVER_PROBE_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .callTimeout(SERVER_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()
                    .forKavita(config)
            }.getOrNull() ?: return@LaunchedEffect
        while (true) {
            ServerReachability.probe(calls, config.serverUrl)
            delay(SERVER_PROBE_INTERVAL_MS)
        }
    }

    KavernTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, einkMode = einkMode) {
        if (showNotifDialog.value && !notifPromptShown && !notificationsEnabled) {
            AlertDialog(
                onDismissRequest = {
                    showNotifDialog.value = false
                    scope.launch { appPreferences.setNotificationsPromptShown(true) }
                },
                title = { Text(text = context.getString(R.string.notifications_prompt_title)) },
                text = { Text(context.getString(R.string.notifications_prompt_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNotifDialog.value = false
                            scope.launch { appPreferences.setNotificationsPromptShown(true) }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val intent =
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                context.startActivity(intent)
                            }
                        },
                    ) { Text(context.getString(R.string.notifications_prompt_accept)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNotifDialog.value = false
                            scope.launch { appPreferences.setNotificationsPromptShown(true) }
                        },
                    ) { Text(context.getString(R.string.notifications_prompt_dismiss)) }
                },
            )
        }
        if (showImportantInfoDialog.value) {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            AlertDialog(
                onDismissRequest = {},
                title = { Text(text = context.getString(R.string.update_info_title)) },
                text = {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = IMPORTANT_INFO_HEADER,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                text = "📌 ${pkg.versionName}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            InfoSection(
                                title = "✨ Added",
                                items = IMPORTANT_INFO_ADDED,
                            )
                            InfoSection(
                                title = "🔁 Changed",
                                items = IMPORTANT_INFO_CHANGED,
                            )
                            InfoSection(
                                title = "🛠️ Fixed",
                                items = IMPORTANT_INFO_FIXED,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showImportantInfoDialog.value = false
                            scope.launch { appPreferences.setImportantInfoVersion(IMPORTANT_INFO_VERSION) }
                        },
                    ) { Text(context.getString(R.string.general_close)) }
                },
            )
        }

        val imageLoader =
            remember(maxThumbnailsParallel, config, einkMode) {
                val dispatcher =
                    Dispatcher().apply {
                        maxRequestsPerHost = maxThumbnailsParallel
                        maxRequests = (maxThumbnailsParallel * 8).coerceIn(16, 32)
                    }
                val okHttpClient =
                    OkHttpClient
                        .Builder()
                        .guardApiKey()
                        .dispatcher(dispatcher)
                        .build()
                ImageLoader
                    .Builder(context)
                    .callFactory(okHttpClient.forKavita(config))
                    // Keep covers on disk so the library still looks right offline. Kavita's
                    // cache headers are short-lived, so ignore them and rely on the size cap.
                    .respectCacheHeaders(false)
                    // Images fade in normally; on e-ink they just appear.
                    .crossfade(!einkMode)
                    .diskCache {
                        DiskCache
                            .Builder()
                            .directory(context.cacheDir.resolve(COVER_CACHE_DIR))
                            .maxSizeBytes(COVER_CACHE_BYTES)
                            .build()
                    }.components {
                        add(
                            ComicPageFallback(context, config) {
                                NetworkMonitor
                                    .getInstance(context, appPreferences)
                                    .status.value.isOnlineAllowed
                            },
                        )
                    }.build()
            }

        CompositionLocalProvider(LocalImageLoader provides imageLoader) {
            val surfaceColor = MaterialTheme.colorScheme.surface
            SideEffect {
                val window: Window? = (view.context as? Activity)?.window
                if (window != null) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    window.statusBarColor = surfaceColor.toArgb()
                }
            }
            val navigateTo: (String) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            val useRail = LocalConfiguration.current.screenWidthDp >= RAIL_MIN_WIDTH_DP
            Scaffold(
                bottomBar = {
                    if (isMainRoute && !useRail) {
                        ShortNavigationBar {
                            MainScreen.items.forEach { screen ->
                                ShortNavigationBarItem(
                                    selected = currentRoute?.startsWith(screen.route) == true,
                                    onClick = { navigateTo(screen.route) },
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = { Text(stringResource(screen.label)) },
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(modifier = Modifier.padding(innerPadding)) {
                    if (isMainRoute && useRail) {
                        WideNavigationRail(windowInsets = WindowInsets(0)) {
                            MainScreen.items.forEach { screen ->
                                WideNavigationRailItem(
                                    railExpanded = false,
                                    selected = currentRoute?.startsWith(screen.route) == true,
                                    onClick = { navigateTo(screen.route) },
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = { Text(stringResource(screen.label)) },
                                )
                            }
                        }
                    }
                    NavHost(
                        navController = navController,
                        startDestination = MainScreen.Home.route,
                        modifier = Modifier.weight(1f),
                        // Navigation's own cross-fade, switched off for e-ink displays.
                        enterTransition = { if (einkMode) EnterTransition.None else fadeIn(tween(SCREEN_FADE_MS)) },
                        exitTransition = { if (einkMode) ExitTransition.None else fadeOut(tween(SCREEN_FADE_MS)) },
                        popEnterTransition = { if (einkMode) EnterTransition.None else fadeIn(tween(SCREEN_FADE_MS)) },
                        popExitTransition = { if (einkMode) ExitTransition.None else fadeOut(tween(SCREEN_FADE_MS)) },
                    ) {
                        composable(MainScreen.Home.route) {
                            HomeScreen(
                                seriesRepository = seriesRepository,
                                cacheManager = cacheManager,
                                appPreferences = appPreferences,
                                onOpenSeries = { seriesId -> navController.navigate("series/$seriesId") },
                                onOpenHistory = { navController.navigate(MainScreen.History.route) },
                                onOpenSettings = { navController.navigate(MainScreen.SETTINGS_ROUTE) },
                            )
                        }
                        composable(
                            route =
                                "${MainScreen.LibraryV2.route}?collectionId={collectionId}&collectionName={collectionName}" +
                                    "&genreId={genreId}&tagId={tagId}&genreName={genreName}&tagName={tagName}",
                            arguments =
                                listOf(
                                    navArgument("collectionId") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("collectionName") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    },
                                    navArgument("genreId") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("tagId") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("genreName") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    },
                                    navArgument("tagName") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    },
                                ),
                        ) {
                            val collectionIdArg = it.arguments?.getInt("collectionId")?.takeIf { id -> id >= 0 }
                            val collectionNameArg = it.arguments?.getString("collectionName")?.takeIf { name -> name.isNotBlank() }
                            val genreIdArg = it.arguments?.getInt("genreId")?.takeIf { id -> id >= 0 }
                            val tagIdArg = it.arguments?.getInt("tagId")?.takeIf { id -> id >= 0 }
                            val genreNameArg = it.arguments?.getString("genreName")?.takeIf { name -> name.isNotBlank() }
                            val tagNameArg = it.arguments?.getString("tagName")?.takeIf { name -> name.isNotBlank() }
                            LibraryV2Screen(
                                libraryRepository = libraryRepository,
                                seriesRepository = seriesRepository,
                                collectionsRepository = collectionsRepository,
                                readingListRepository = readingListRepository,
                                personRepository = personRepository,
                                cacheManager = cacheManager,
                                appPreferences = appPreferences,
                                onOpenSeries = { seriesId ->
                                    navController.navigate("series/$seriesId")
                                },
                                onOpenSettings = { navController.navigate(MainScreen.SETTINGS_ROUTE) },
                                initialCollectionId = collectionIdArg,
                                initialCollectionName = collectionNameArg,
                                initialGenreId = genreIdArg,
                                initialGenreName = genreNameArg,
                                initialTagId = tagIdArg,
                                initialTagName = tagNameArg,
                            )
                        }
                        composable(MainScreen.History.route) {
                            HistoryScreen(
                                appPreferences = appPreferences,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(MainScreen.Downloads.route) {
                            val vm: DownloadQueueViewModel =
                                viewModel(factory = DownloadQueueViewModelFactory.create(appPreferences, cacheManager))
                            DownloadQueueScreen(
                                viewModel = vm,
                                onOpenSeries = { seriesId -> navController.navigate("series/$seriesId") },
                            )
                        }
                        composable(MainScreen.SETTINGS_ROUTE) {
                            SettingsScreen(
                                appPreferences = appPreferences,
                                authRepository = authRepository,
                                cacheManager = cacheManager,
                                onOpenAbout = { navController.navigate("settings/about") },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "series/{seriesId}",
                            arguments = listOf(navArgument("seriesId") { type = NavType.IntType }),
                        ) { entry ->
                            val seriesId = entry.arguments?.getInt("seriesId") ?: return@composable
                            val readerReturn =
                                entry.savedStateHandle
                                    .getStateFlow<ReaderReturn?>(
                                        "reader_return",
                                        null,
                                    ).collectAsState(initial = null)
                            val refreshSignal =
                                entry.savedStateHandle
                                    .getStateFlow<Boolean>(
                                        "series_refresh",
                                        false,
                                    ).collectAsState(initial = false)
                            SeriesDetailScreenV2(
                                seriesId = seriesId,
                                appPreferences = appPreferences,
                                collectionsRepository = collectionsRepository,
                                readerRepository = readerRepository,
                                cacheManager = cacheManager,
                                onOpenReader = { chapterId, page, sid, vid, fmt ->
                                    navController.navigate("reader/$chapterId?page=$page&sid=$sid&vid=$vid&fmt=${fmt ?: -1}")
                                },
                                onOpenVolume = { volumeId ->
                                    navController.navigate("volume/$volumeId")
                                },
                                onOpenSeries = { id ->
                                    navController.navigate("series/$id")
                                },
                                onOpenBrowseGenre = { id, name ->
                                    val encoded = Uri.encode(name)
                                    navController.navigate("${MainScreen.LibraryV2.route}?genreId=$id&genreName=$encoded")
                                },
                                onOpenBrowseTag = { id, name ->
                                    val encoded = Uri.encode(name)
                                    navController.navigate("${MainScreen.LibraryV2.route}?tagId=$id&tagName=$encoded")
                                },
                                onOpenCollection = { id, name ->
                                    val encoded = Uri.encode(name)
                                    navController.navigate("${MainScreen.LibraryV2.route}?collectionId=$id&collectionName=$encoded")
                                },
                                readerReturn = readerReturn.value,
                                onConsumeReaderReturn = { entry.savedStateHandle["reader_return"] = null },
                                refreshSignal = refreshSignal.value,
                                onConsumeRefreshSignal = { entry.savedStateHandle["series_refresh"] = false },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "volume/{volumeId}",
                            arguments = listOf(navArgument("volumeId") { type = NavType.IntType }),
                        ) { entry ->
                            val volumeId = entry.arguments?.getInt("volumeId") ?: return@composable
                            val readerReturn =
                                entry.savedStateHandle
                                    .getStateFlow<ReaderReturn?>(
                                        "reader_return",
                                        null,
                                    ).collectAsState(initial = null)
                            com.iquintero.kavern.ui.seriesdetail.VolumeDetailScreenV2(
                                volumeId = volumeId,
                                appPreferences = appPreferences,
                                readerRepository = readerRepository,
                                readerReturn = readerReturn.value,
                                onConsumeReaderReturn = {
                                    entry.savedStateHandle["reader_return"] = null
                                    navController.previousBackStackEntry?.savedStateHandle?.set("series_refresh", true)
                                },
                                onOpenReader = { chapterId, page, sid, vid, fmt ->
                                    navController.navigate("reader/$chapterId?page=$page&sid=$sid&vid=$vid&fmt=${fmt ?: 0}")
                                },
                                onBack = {
                                    navController.previousBackStackEntry?.savedStateHandle?.set("series_refresh", true)
                                    navController.popBackStack()
                                },
                            )
                        }
                        composable("settings/about") {
                            com.iquintero.kavern.ui.settings.screens.SettingsAboutScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "reader/{chapterId}?page={page}&sid={sid}&vid={vid}&fmt={fmt}",
                            arguments =
                                listOf(
                                    navArgument("chapterId") { type = NavType.IntType },
                                    navArgument("page") {
                                        type = NavType.IntType
                                        defaultValue = 0
                                    },
                                    navArgument("sid") {
                                        type = NavType.IntType
                                        defaultValue = 0
                                    },
                                    navArgument("vid") {
                                        type = NavType.IntType
                                        defaultValue = 0
                                    },
                                    navArgument("fmt") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                ),
                        ) { entry ->
                            val chId = entry.arguments?.getInt("chapterId") ?: return@composable
                            val page = entry.arguments?.getInt("page")
                            val sid = entry.arguments?.getInt("sid")?.takeIf { it != 0 }
                            val vid = entry.arguments?.getInt("vid")?.takeIf { it != 0 }
                            val fmt = entry.arguments?.getInt("fmt")?.takeIf { it != -1 }
                            ReaderScreen(
                                chapterId = chId,
                                initialPage = page,
                                readerRepository = readerRepository,
                                appPreferences = appPreferences,
                                seriesId = sid,
                                volumeId = vid,
                                serverUrl = config.serverUrl,
                                apiKey = config.apiKey,
                                formatId = fmt,
                                onBack = { _, pIdx, _, vIdBack ->
                                    navController.previousBackStackEntry?.savedStateHandle?.set(
                                        "reader_return",
                                        ReaderReturn(
                                            volumeId = vIdBack ?: 0,
                                            page = pIdx,
                                        ),
                                    )
                                    navController.popBackStack()
                                },
                                onNavigateToChapter = { targetChapter, targetPage, targetSid, targetVid ->
                                    val nextSid = targetSid ?: sid ?: 0
                                    val nextVid = targetVid ?: vid ?: 0
                                    val fmtValue = fmt ?: -1
                                    navController.navigate("reader/$targetChapter?page=${targetPage ?: 0}&sid=$nextSid&vid=$nextVid&fmt=$fmtValue") {
                                        popUpTo("reader/$chId?page=${page ?: 0}&sid=${sid ?: 0}&vid=${vid ?: 0}&fmt=$fmtValue") { inclusive = true }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
