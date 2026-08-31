package io.lukia.features.reader.presentation.components.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.surface.GlassSurface
import io.lukia.shared.presentation.components.surface.GlassTone
import io.lukia.shared.presentation.theme.Space
import kotlinx.coroutines.delay

/** Long enough to read a sentence twice, short enough not to sit over the book. */
private const val NOTICE_MILLIS = 6_000L

/** Wide enough for two lines of a sentence, never wider than a phone. */
private val NoticeMaxWidth: Dp = 420.dp

/**
 * Something the reader has to be told about the book in front of them.
 *
 * Not a snackbar, because the reader has no scaffold to hang one from and does
 * not want one: everything else in this screen floats over the page in glass,
 * and a Material bar sliding in from the bottom edge would be the only part of
 * the reader that looks like the rest of the app.
 *
 * It takes itself away. What it says is never an action - it explains what the
 * app did and why - so leaving a dismiss button over the page would be asking
 * the reader to tidy up after a message they did not ask for. [onDismiss] fires
 * once it has been up long enough to read, and is what stops it coming back.
 */
@Composable
internal fun ReaderNotice(
    text: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(visible, text) {
        if (!visible) return@LaunchedEffect

        delay(NOTICE_MILLIS)
        dismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(Space.md),
    ) {
        GlassSurface(shape = MaterialTheme.shapes.large, tone = GlassTone.Panel) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .widthIn(max = NoticeMaxWidth)
                    .padding(horizontal = Space.md, vertical = Space.sm),
            )
        }
    }
}
