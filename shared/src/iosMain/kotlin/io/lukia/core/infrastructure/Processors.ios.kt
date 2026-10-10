package io.lukia.core.infrastructure

import platform.Foundation.NSProcessInfo

/**
 * The active count rather than the total: an iPhone reports every core it has,
 * including the ones the system has parked to save power, and work handed to a
 * core that is not running is work waiting behind work.
 */
internal actual fun availableProcessors(): Int =
    NSProcessInfo.processInfo.activeProcessorCount.toInt()
