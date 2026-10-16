package io.lukia.features.sync.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.settings.presentation.screens.SettingsSection
import io.lukia.features.settings.presentation.screens.SettingsToggle
import io.lukia.resources.Res
import io.lukia.resources.sync_auto_upload
import io.lukia.resources.sync_auto_upload_detail
import io.lukia.resources.sync_checked_hours
import io.lukia.resources.sync_checked_just_now
import io.lukia.resources.sync_checked_minutes
import io.lukia.resources.sync_failed
import io.lukia.resources.sync_not_yet
import io.lukia.resources.sync_now
import io.lukia.resources.sync_pending
import io.lukia.resources.sync_retry_minutes
import io.lukia.resources.sync_retry_soon
import io.lukia.resources.sync_running
import io.lukia.resources.sync_title
import io.lukia.resources.sync_unreachable
import io.lukia.resources.sync_up_to_date
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.theme.Space
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

/**
 * Whether this device is level with the server: up to date and when it last
 * checked, what is still waiting to go up, or that the server cannot be
 * reached and when it will be tried again - and a way to check now. Shown
 * under the Account page while signed in.
 */
@Composable
fun SyncStatusCard(groupColor: Color, modifier: Modifier = Modifier, viewModel: SyncStatusViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    // "Checked 2 minutes ago" has to move on while the page is open.
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TickMillis)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }

    SyncStatusContent(state, now, viewModel::syncNow, groupColor, modifier, viewModel::setAutoUpload)
}

@Composable
internal fun SyncStatusContent(
    state: SyncStatusUi,
    now: Long,
    onSyncNow: () -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
    onAutoUploadChange: (Boolean) -> Unit = {},
) {
    val serverName = state.serverName ?: return
    val status = state.status

    SettingsSection(title = stringResource(Res.string.sync_title), groupColor = groupColor, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(text = headline(state, serverName), style = MaterialTheme.typography.bodyMedium)
            details(state, now).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.problem != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            LukiaButton(
                label = stringResource(Res.string.sync_now),
                onClick = onSyncNow,
                variant = ButtonVariant.Secondary,
                enabled = !status.running,
            )
            SettingsToggle(
                label = stringResource(Res.string.sync_auto_upload),
                checked = state.autoUpload,
                onChange = onAutoUploadChange,
                detail = stringResource(Res.string.sync_auto_upload_detail),
                modifier = Modifier.padding(top = Space.sm),
            )
        }
    }
}

/** The one thing worth knowing first. */
@Composable
private fun headline(state: SyncStatusUi, serverName: String): String {
    val status = state.status
    return when {
        status.running -> stringResource(Res.string.sync_running)
        status.problem == AccountProblem.ServerUnreachable -> stringResource(Res.string.sync_unreachable, serverName)
        status.problem != null -> stringResource(Res.string.sync_failed, serverName)
        status.lastSyncedAt == null -> stringResource(Res.string.sync_not_yet)
        state.pending > 0 -> pendingText(state.pending)
        else -> stringResource(Res.string.sync_up_to_date)
    }
}

/** When it was last checked, or when it will be tried again and what is waiting meanwhile. */
@Composable
private fun details(state: SyncStatusUi, now: Long): List<String> {
    val status = state.status
    val lines = mutableListOf<String>()

    status.retryAt?.takeIf { status.problem != null }?.let { retryAt ->
        val minutes = ((retryAt - now + MillisPerMinute - 1) / MillisPerMinute).toInt()
        lines += if (minutes < 1) {
            stringResource(Res.string.sync_retry_soon)
        } else {
            pluralStringResource(Res.plurals.sync_retry_minutes, minutes, minutes)
        }
        if (state.pending > 0) {
            lines += pendingText(state.pending)
        }
    }

    status.lastSyncedAt?.let { lines += checkedAgo(now - it) }
    return lines
}

@Composable
private fun pendingText(pending: Long): String =
    pluralStringResource(Res.plurals.sync_pending, pending.toInt(), pending.toInt())

@Composable
private fun checkedAgo(elapsedMillis: Long): String {
    val minutes = (elapsedMillis / MillisPerMinute).toInt()
    val hours = minutes / MinutesPerHour
    return when {
        minutes < 1 -> stringResource(Res.string.sync_checked_just_now)
        hours < 1 -> pluralStringResource(Res.plurals.sync_checked_minutes, minutes, minutes)
        else -> pluralStringResource(Res.plurals.sync_checked_hours, hours, hours)
    }
}

private const val TickMillis = 20_000L
private const val MillisPerMinute = 60_000L
private const val MinutesPerHour = 60
