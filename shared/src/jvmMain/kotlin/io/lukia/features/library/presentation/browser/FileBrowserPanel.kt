package io.lukia.features.library.presentation.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.surface.EmptyState
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.components.surface.OverlayPanel
import io.lukia.shared.presentation.components.surface.OverlayPanelTopClearance
import io.lukia.shared.presentation.theme.Space
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * How much room the list is given, whatever is in the folder.
 *
 * Held rather than fitted so that walking from a folder of two books into one of
 * forty is walking rather than the panel growing and shrinking under the
 * reader's pointer - the same reason the settings hold a height across their
 * groups.
 */
private val ListHeight: Dp = 320.dp

/** The panel is wider than a detail panel: it is a list of names, two levels deep. */
private val PanelWidth: Dp = 520.dp

/**
 * Choosing a book or a folder, inside the app's own window.
 *
 * On macOS the app still uses the system's own `NSOpenPanel`, which is what
 * every other application there opens. Windows and Linux could only offer
 * Swing's chooser, a window drawn by Java in neither the app's design nor the
 * system's, arriving outside the app looking like a different program. A panel
 * over the library is smaller than either, and is at least Lukia's.
 *
 * It shows only what a reader can act on - folders, and the formats the library
 * reads - so the list is a list of books rather than of files.
 */
@Composable
internal fun FileBrowserPanel(
    pick: DesktopPick,
    modifier: Modifier = Modifier,
    /** Where the folder is read. Injectable so a test can read one deterministically. */
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    var folder by remember { mutableStateOf(DesktopPicks.lastFolder()) }
    var chosen by remember { mutableStateOf(emptySet<String>()) }

    val places = remember { browserPlaces() }
    val trail = remember(folder) { folder.trail() }
    val listState = rememberLazyListState()

    // Off the main thread, because a folder of several thousand files is a
    // directory read and a sort, and doing that in composition would drop the
    // frames the panel is arriving on. Null until it has been read, which is not
    // the same as having read it and found nothing - see [BrowserList].
    val entries by produceState<List<BrowserEntry>?>(null, folder, pick.foldersOnly, dispatcher) {
        value = withContext(dispatcher) { listFolder(folder, pick.foldersOnly) }
    }

    val enter: (BrowserEntry) -> Unit = { entry ->
        folder = File(entry.path)
        DesktopPicks.remember(folder)
    }

    OverlayPanel(
        visible = true,
        onDismiss = DesktopPicks::cancel,
        widthCap = PanelWidth,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Space.xl)) {
            Spacer(modifier = Modifier.height(OverlayPanelTopClearance))

            // Says where what is chosen ends up, which the button underneath - a
            // count of what has been picked so far - deliberately does not.
            Text(
                text = if (pick.foldersOnly) {
                    "Choose a library folder"
                } else {
                    "Add books to the library"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Space.xxl),
            )

            BrowserHeader(
                places = places,
                trail = trail,
                current = folder.absolutePath,
                onGo = enter,
            )

            LukiaDivider()

            BrowserList(
                entries = entries,
                chosen = chosen,
                listState = listState,
                onEnter = enter,
                onPick = { path -> chosen = chosen.toggling(path) },
                modifier = Modifier.fillMaxWidth().height(ListHeight),
            )

            LukiaDivider()

            BrowserActions(
                pick = pick,
                folder = folder,
                chosen = chosen,
                modifier = Modifier.padding(top = Space.xl, start = Space.xxl, end = Space.xxl),
            )
        }
    }
}

/** The places to start from, and the way back out of where the reader has got to. */
@Composable
private fun BrowserHeader(
    places: List<BrowserEntry>,
    trail: List<BrowserEntry>,
    current: String,
    onGo: (BrowserEntry) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.md),
        modifier = Modifier.padding(
            start = Space.xxl,
            end = Space.xxl,
            top = Space.lg,
            bottom = Space.lg,
        ),
    ) {
        PlacesRow(places = places, current = current, onGo = onGo)

        PathBar(trail = trail, onGo = onGo)
    }
}

/** What is in the folder, or a word about why nothing is. */
@Composable
private fun BrowserList(
    entries: List<BrowserEntry>?,
    chosen: Set<String>,
    listState: LazyListState,
    onEnter: (BrowserEntry) -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        // Still being read. The room is held rather than filled, so the panel
        // does not change height under the reader between one folder and the
        // next - and so that a folder with something in it is never announced as
        // empty on the way to showing what it holds.
        entries == null -> Box(modifier = modifier)

        entries.isEmpty() -> EmptyState(
            title = "Nothing to open here",
            message = "This folder holds no books and no folders to look in.",
            modifier = modifier,
        )

        else -> LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
        ) {
            items(entries, key = { it.path }) { entry ->
                BrowserRow(
                    entry = entry,
                    picked = entry.path in chosen,
                    onClick = { if (entry.isFolder) onEnter(entry) else onPick(entry.path) },
                )
            }
        }
    }
}

/** The way out, and the way to answer. */
@Composable
private fun BrowserActions(
    pick: DesktopPick,
    folder: File,
    chosen: Set<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.lg, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        LukiaButton(
            label = "Cancel",
            onClick = DesktopPicks::cancel,
            variant = ButtonVariant.Ghost,
        )

        LukiaButton(
            label = pick.confirmLabel(folder, chosen.size),
            onClick = {
                DesktopPicks.answer(
                    if (pick.foldersOnly) listOf(folder.absolutePath) else chosen.toList(),
                )
            },
            // A folder is always somewhere, so there is always one to choose;
            // books have to have been picked first.
            enabled = pick.foldersOnly || chosen.isNotEmpty(),
        )
    }
}

/**
 * What the confirming button says it will do, which is the one place a reader
 * can check what pressing it means.
 */
private fun DesktopPick.confirmLabel(folder: File, count: Int): String = when {
    foldersOnly -> "Use ${folder.name.ifEmpty { folder.path }}"
    count == 0 -> "Add books"
    count == 1 -> "Add 1 book"
    else -> "Add $count books"
}

private fun Set<String>.toggling(path: String): Set<String> =
    if (path in this) this - path else this + path
