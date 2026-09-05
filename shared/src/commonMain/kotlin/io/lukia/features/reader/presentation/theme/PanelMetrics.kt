package io.lukia.features.reader.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How wide the reader's own floating panels are drawn on a window with room.
 *
 * The settings panel and the contents panel hang from the same toolbar, open the
 * same way, and are as often as not opened one after the other - so the second
 * one appearing at a different width than the first reads as the chrome moving
 * rather than as a different panel. Each carried its own copy of this number
 * until they were measured side by side.
 *
 * A fixed width rather than a fraction: what it is sized for is a chapter title
 * staying on one line and a label sitting beside its control, neither of which
 * gets easier on a wider monitor.
 */
internal val ReaderPanelWidth: Dp = 380.dp
