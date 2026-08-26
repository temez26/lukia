package io.lukia.features.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.features.profile.application.GetReadingSummaryUseCase
import io.lukia.features.profile.domain.ProfileAvatar
import io.lukia.features.profile.domain.ProfilePicture
import io.lukia.features.profile.domain.ProfileTint
import io.lukia.features.profile.domain.ReadingSummary
import io.lukia.features.profile.domain.UserProfile
import io.lukia.features.profile.infrastructure.ProfileSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The profile screen's half of the reader: who they are, what they look like,
 * and what their reading adds up to.
 *
 * The three are held apart because they come from different places and fail
 * differently. The name and the chosen disc are short strings, already in
 * memory, and cannot fail to be read - so they are exposed straight from the
 * repository with no [UiState] around them. The picture is a database read that
 * may find nothing, which is not a failure. The summary is a read of the whole
 * library, which can be slow and can fail, and gets the app's usual three
 * states.
 */
class ProfileViewModel(
    private val profiles: ProfileSettingsRepository,
    private val getReadingSummary: GetReadingSummaryUseCase,
) : ViewModel() {

    /**
     * Straight from the repository rather than copied here: the profile outlives
     * this screen - onboarding writes it before any of this exists - and a
     * mirror of it would be one more thing that could disagree with what is
     * stored.
     */
    val profile: StateFlow<UserProfile?> = profiles.profile

    val picture: StateFlow<ProfilePicture?> = profiles.picture

    private val _summary = MutableStateFlow<UiState<ReadingSummary>>(UiState.Loading)
    val summary: StateFlow<UiState<ReadingSummary>> = _summary.asStateFlow()

    init {
        refresh()

        viewModelScope.launch { profiles.loadPicture() }
    }

    /**
     * Reads the library, the positions and the sittings again.
     *
     * Called when the screen is entered as well as on construction, because this
     * ViewModel outlives a trip into a book: the profile is reached from a
     * control on the shelf and left by going back to it, and the pages and the
     * minutes in between are exactly what somebody returning here wants counted.
     */
    fun refresh() {
        viewModelScope.launch {
            _summary.value = runCatchingApp { getReadingSummary() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Failure(it.toAppError()) },
            )
        }
    }

    /** Ignores a name that cannot be used - see [ProfileSettingsRepository.setUsername]. */
    fun rename(username: String) {
        profiles.setUsername(username)
    }

    fun setAvatar(avatar: ProfileAvatar) {
        profiles.setAvatar(avatar)
    }

    fun setTint(tint: ProfileTint) {
        profiles.setTint(tint)
    }

    /**
     * Keeps a picture the reader has just chosen, and wears it.
     *
     * Null is what a cancelled picker hands back, and is not an event: the
     * reader closed a dialog, which should leave everything exactly as it was.
     */
    fun setPicture(bytes: ByteArray?) {
        if (bytes == null) return

        viewModelScope.launch { profiles.setPicture(bytes) }
    }

    fun clearPicture() {
        viewModelScope.launch { profiles.clearPicture() }
    }
}
