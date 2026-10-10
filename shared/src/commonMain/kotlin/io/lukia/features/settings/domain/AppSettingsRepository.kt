package io.lukia.features.settings.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * How the app itself is set up, live and kept between launches.
 *
 * One for the process, so a change made on one screen is what every other
 * screen reads on its very next look.
 */
interface AppSettingsRepository {

    val settings: StateFlow<AppSettings>

    fun update(settings: AppSettings)
}
