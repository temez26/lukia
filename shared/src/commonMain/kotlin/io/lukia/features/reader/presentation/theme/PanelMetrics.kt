package io.lukia.features.reader.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How wide the reader's own floating panels are drawn on a window with room.
 *
 * The settings and contents panels hang from the same toolbar and are often
 * opened one after the other, so a second one at a different width reads as the
 * chrome moving rather than as a different panel. Each carried its own copy of
 * this number until they were measured side by side.
 *
 * Fixed rather than a fraction: it is sized for a chapter title staying on one
 * line and a label sitting beside its control, neither of which gets easier on a
 * wider monitor.
 */
internal val ReaderPanelWidth: Dp = 380.dp
