package io.lukia.features.translate.domain.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * How translating is configured, live and kept between launches.
 *
 * One for the process, so a change made on one screen is what every other
 * screen reads on its very next look.
 */
interface TranslationSettingsRepository {

    val settings: StateFlow<TranslationSettings>

    fun update(settings: TranslationSettings)
}
