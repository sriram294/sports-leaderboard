package com.org.playboard.ui.switcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.org.playboard.data.group.GroupsLoadState
import com.org.playboard.data.model.Group
import com.org.playboard.ui.components.GroupAvatar
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted
import com.org.playboard.ui.theme.TextPrimary

/**
 * The shared group switcher, rendered at the top of every tab (replacing the
 * old per-page titles). Shows the active group with an expandable panel to
 * switch/create/join/invite; when the user has no group it offers a create/join
 * entry point instead. See docs/requirements/00-overview.md § Group.
 */
@Composable
fun GroupSwitcher(
    modifier: Modifier = Modifier,
    viewModel: GroupSwitcherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    GroupSwitcherContent(
        uiState = uiState,
        modifier = modifier,
        onToggle = viewModel::onToggled,
        onGroupSelected = viewModel::onGroupSelected,
        onCreateOrJoinGroupClicked = viewModel::onCreateOrJoinGroupClicked,
        onInvitePlayersClicked = viewModel::onInvitePlayersClicked,
        onRetry = viewModel::refresh,
    )

    uiState.groupActionSheet?.let { sheet ->
        GroupActionSheet(
            state = sheet,
            onModeChanged = viewModel::onSheetModeChanged,
            onInputChanged = viewModel::onSheetInputChanged,
            onSubmit = viewModel::onSheetSubmit,
            onDismiss = viewModel::onSheetDismissed,
        )
    }

    uiState.inviteSheet?.let { sheet ->
        InviteSheet(
            state = sheet,
            onDismiss = viewModel::onInviteSheetDismissed,
            onRetry = viewModel::onInviteRetry,
        )
    }
}

@Composable
private fun GroupSwitcherContent(
    uiState: GroupSwitcherUiState,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onCreateOrJoinGroupClicked: () -> Unit,
    onInvitePlayersClicked: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val group = uiState.selectedGroup
        if (group != null) {
            GroupSwitcherCard(group = group, isExpanded = uiState.isExpanded, onToggle = onToggle)
            AnimatedVisibility(visible = uiState.isExpanded) {
                YourGroupsPanel(
                    groups = uiState.groups,
                    selectedGroupId = group.id,
                    canInviteSelected = group.canInvite,
                    onGroupSelected = onGroupSelected,
                    onCreateOrJoinGroupClicked = onCreateOrJoinGroupClicked,
                    onInvitePlayersClicked = onInvitePlayersClicked,
                )
            }
        } else {
            when (uiState.loadState) {
                GroupsLoadState.LOADING -> StatusCard(text = "Loading groups…", showSpinner = true)
                GroupsLoadState.FAILED -> StatusCard(text = "Couldn't load groups. Tap to retry.", onClick = onRetry)
                GroupsLoadState.LOADED ->
                    StatusCard(text = "＋  Create or join a group", onClick = onCreateOrJoinGroupClicked, accent = true)
            }
        }
    }
}

@Composable
private fun GroupSwitcherCard(group: Group, isExpanded: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            GroupAvatar(name = group.name, avatarColorHex = group.avatarColor, size = 40.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(text = "GROUP", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
            Text(
                text = "${group.memberCount} players ${if (isExpanded) "▴" else "▾"}",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = TextMuted,
            )
        }
    }
}

/** Compact header card for the no-group states (loading / failed / create-or-join prompt). */
@Composable
private fun StatusCard(
    text: String,
    showSpinner: Boolean = false,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            if (showSpinner) {
                CircularProgressIndicator(color = BrandLime, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 12.dp),
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (accent) BrandLime else TextMuted,
                )
            }
        }
    }
}

@Composable
private fun YourGroupsPanel(
    groups: List<Group>,
    selectedGroupId: String,
    canInviteSelected: Boolean,
    onGroupSelected: (String) -> Unit,
    onCreateOrJoinGroupClicked: () -> Unit,
    onInvitePlayersClicked: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "YOUR GROUPS",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            groups.forEach { group ->
                val isSelected = group.id == selectedGroupId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGroupSelected(group.id) }
                        .background(if (isSelected) BrandLime.copy(alpha = 0.08f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    GroupAvatar(name = group.name, avatarColorHex = group.avatarColor, size = 36.dp)
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) BrandLime else TextPrimary,
                        )
                        Text(
                            text = "${group.memberCount} players · ${group.matchCount} matches",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                            color = TextMuted,
                        )
                    }
                    if (isSelected) {
                        Text(text = "✓", color = BrandLime, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            HorizontalDivider(
                color = TextMuted.copy(alpha = 0.15f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (canInviteSelected) {
                PanelActionRow(icon = "↗", label = "Invite players", onClick = onInvitePlayersClicked)
            }
            PanelActionRow(icon = "+", label = "Create or join a group", onClick = onCreateOrJoinGroupClicked)
        }
    }
}

/** A tappable action row at the foot of the group switcher (invite, create/join). */
@Composable
private fun PanelActionRow(icon: String, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = icon, color = BrandLime, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private val previewGroups = listOf(
    Group(id = "g1", name = "Saturday Smashers", avatarColor = "#C7EA2B", memberCount = 6, matchCount = 10, myRole = "owner"),
    Group(id = "g2", name = "Office League", avatarColor = "#3DB4FF", memberCount = 4, matchCount = 4, myRole = "member"),
)

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun GroupSwitcherCollapsedPreview() {
    PlayboardTheme {
        GroupSwitcherContent(
            uiState = GroupSwitcherUiState(
                groups = previewGroups,
                selectedGroup = previewGroups.first(),
                loadState = GroupsLoadState.LOADED,
            ),
            onToggle = {},
            onGroupSelected = {},
            onCreateOrJoinGroupClicked = {},
            onInvitePlayersClicked = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun GroupSwitcherExpandedPreview() {
    PlayboardTheme {
        GroupSwitcherContent(
            uiState = GroupSwitcherUiState(
                groups = previewGroups,
                selectedGroup = previewGroups.first(),
                loadState = GroupsLoadState.LOADED,
                isExpanded = true,
            ),
            onToggle = {},
            onGroupSelected = {},
            onCreateOrJoinGroupClicked = {},
            onInvitePlayersClicked = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun GroupSwitcherNoGroupPreview() {
    PlayboardTheme {
        GroupSwitcherContent(
            uiState = GroupSwitcherUiState(loadState = GroupsLoadState.LOADED),
            onToggle = {},
            onGroupSelected = {},
            onCreateOrJoinGroupClicked = {},
            onInvitePlayersClicked = {},
            onRetry = {},
        )
    }
}
