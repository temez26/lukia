package io.lukia.core.presentation

import androidx.compose.runtime.Composable
import io.lukia.resources.Res
import io.lukia.resources.menu_find
import org.jetbrains.compose.resources.stringResource

/**
 * The macOS menu bar's Find item, read here because the desktop entry point that
 * builds the menu cannot reach this module's resources.
 */
@Composable
fun findMenuLabel(): String = stringResource(Res.string.menu_find)
