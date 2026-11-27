package com.iquintero.kavern.ui.navigation

import com.iquintero.kavern.core.network.grimmory.isGrimmory
import com.iquintero.kavern.core.storage.AppConfig

/**
 * Which kind of server the app is connected to. Each mode has its own screens, view models and data, so a feature for
 * one never has to be built around the other.
 */
enum class AppMode {
    Kavita,
    Grimmory,
    ;

    companion object {
        fun of(config: AppConfig): AppMode = if (config.isGrimmory) Grimmory else Kavita
    }
}
