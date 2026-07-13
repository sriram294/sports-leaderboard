package com.org.playboard.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.group.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the one cross-tab signal the [MainScreen] shell needs on its own: the
 * active group's id. The shell uses it to drop transient in-place state — notably
 * the Board's leaderboard drill-down — when the user switches groups, since a
 * player from the previous group's leaderboard doesn't belong in the new group's
 * view.
 *
 * Emits only on an actual id change ([distinctUntilChanged]): a silent group-list
 * refresh (pull-to-refresh / foreground resync) re-emits the same group with an
 * updated member/match count, which must not count as a switch.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    groupRepository: GroupRepository,
) : ViewModel() {

    val selectedGroupId: StateFlow<String?> =
        groupRepository.selectedGroup
            .map { it?.id }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
