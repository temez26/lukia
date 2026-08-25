package io.lukia.features.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.features.profile.application.GetReadingSummaryUseCase
import io.lukia.features.profile.domain.ReadingSummary
import io.lukia.features.profile.domain.UserProfile
import io.lukia.features.profile.infrastructure.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The profile screen's half of the reader: who they are, and what their reading
 * adds up to.
 *
 * The two are held apart because they come from different places and fail
 * differently. The name is one short string, already in memory, and cannot fail
 * to be read - so it is exposed straight from the repository, with no
 * [UiState] around it. The summary is a read of the whole library, which can be
 * slow and can fail, and gets the app's usual three states.
 */
class ProfileViewModel(
    private val profiles: ProfileRepository,
    private val getReadingSummary: GetReadingSummaryUseCase,
) : ViewModel() {

    /**
     * Straight from the repository rather than copied here: the profile outlives
     * this screen - onboarding writes it before any of this exists - and a
     * mirror of it would be one more thing that could disagree with what is
     * stored.
     */
    val profile: StateFlow<UserProfile?> = profiles.profile

    private val _summary = MutableStateFlow<UiState<ReadingSummary>>(UiState.Loading)
    val summary: StateFlow<UiState<ReadingSummary>> = _summary.asStateFlow()

    init {
        refresh()
    }

    /**
     * Reads the library and the positions again.
     *
     * Called when the screen is entered as well as on construction, because this
     * ViewModel outlives a trip into a book: the profile is reached from a menu
     * and left by going back to it, and the pages read in between are exactly
     * what somebody returning here wants to see counted.
     */
    fun refresh() {
        viewModelScope.launch {
            _summary.value = runCatchingApp { getReadingSummary() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Failure(it.toAppError()) },
            )
        }
    }

    /** Ignores a name that cannot be used - see [ProfileRepository.setUsername]. */
    fun rename(username: String) {
        profiles.setUsername(username)
    }
}
