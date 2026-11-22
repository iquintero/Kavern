package com.iquintero.kavern.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.ui.graphics.vector.ImageVector
import com.iquintero.kavern.R

sealed class MainScreen(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    object Home : MainScreen("home", R.string.nav_home, Icons.Filled.Home)

    object LibraryV2 : MainScreen("library_v2", R.string.nav_library, Icons.Filled.LibraryBooks)

    object History : MainScreen("history", R.string.nav_history, Icons.Filled.History)

    object Downloads : MainScreen("downloads", R.string.nav_downloads, Icons.Filled.Download)

    companion object {
        /** Top-level destinations shown in the navigation bar or rail (three to five, per Material). */
        val items = listOf(Home, LibraryV2, History, Downloads)

        /** Settings is opened from the Home and Library top bars, not from the navigation bar. */
        const val SETTINGS_ROUTE = "settings"
    }
}
