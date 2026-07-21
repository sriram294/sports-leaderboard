package com.org.playboard.ui.switcher

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.org.playboard.data.group.GroupsLoadState
import com.org.playboard.data.model.Group
import com.org.playboard.ui.components.GroupAvatar
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.R

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

    // Re-sync whenever the app returns to the foreground, so membership/match
    // changes made elsewhere appear without a relogin. The effect also fires on the
    // initial resume right after composition; skip that one (the ViewModel's init
    // already did the first fetch) so we don't double the startup load.
    var skipFirstResume by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        if (skipFirstResume) skipFirstResume = false else viewModel.onAppResumed()
        onPauseOrDispose { }
    }

    GroupSwitcherContent(
        uiState = uiState,
        modifier = modifier,
        onToggle = viewModel::onToggled,
        onEditGroup = viewModel::onEditGroupClicked,
        onGroupSelected = viewModel::onGroupSelected,
        onCreateOrJoinGroupClicked = viewModel::onCreateOrJoinGroupClicked,
        onInvitePlayersClicked = viewModel::onInvitePlayersClicked,
        onAddMemberClicked = viewModel::onAddMemberClicked,
        onRetry = viewModel::refresh,
    )

    uiState.addMemberSheet?.let { sheet ->
        AddMemberSheet(
            state = sheet,
            onEmailChanged = viewModel::onAddMemberEmailChanged,
            onNameChanged = viewModel::onAddMemberNameChanged,
            onSubmit = viewModel::onAddMemberSubmit,
            onDismiss = viewModel::onAddMemberDismissed,
        )
    }

    uiState.renameSheet?.let { sheet ->
        RenameGroupSheet(
            state = sheet,
            onInputChanged = viewModel::onRenameInputChanged,
            onSubmit = viewModel::onRenameSubmit,
            onDismiss = viewModel::onRenameSheetDismissed,
        )
    }

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
    onEditGroup: (String,String) -> Unit,
    onGroupSelected: (String) -> Unit,
    onCreateOrJoinGroupClicked: () -> Unit,
    onInvitePlayersClicked: () -> Unit,
    onAddMemberClicked: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val group = uiState.selectedGroup
        if (group != null) {
            GroupSwitcherCard(
                group = group,
                isExpanded = uiState.isExpanded,
                onToggle = onToggle,
            )
            AnimatedVisibility(visible = uiState.isExpanded) {
                YourGroupsPanel(
                    groups = uiState.groups,
                    selectedGroupId = group.id,
                    canInviteSelected = group.canInvite,
                    canManageSelected = group.canManage,
                    onGroupSelected = onGroupSelected,
                    onCreateOrJoinGroupClicked = onCreateOrJoinGroupClicked,
                    onInvitePlayersClicked = onInvitePlayersClicked,
                    onAddMemberClicked = onAddMemberClicked,
                    onEditGroup = onEditGroup,
                )
            }
        } else {
            when (uiState.loadState) {
                GroupsLoadState.LOADING -> StatusCard(text = "Loading groups…", showSpinner = true)
                GroupsLoadState.FAILED -> StatusCard(text = "Couldn't load groups. Tap to retry.", onClick = onRetry)
                GroupsLoadState.LOADED ->
                    StatusCard(
                        text = "Create or join a group",
                        accent = true,
                        icon = R.drawable.ic_create,
                        onClick = onCreateOrJoinGroupClicked,
                    )
            }
        }
    }
}

@Composable
private fun GroupSwitcherCard(
    group: Group,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    // Slim single-line pill: the app header above it already names the app, so the
    // switcher only needs to say which group is active. Dropping the "GROUP" label
    // and shrinking the avatar keeps the leaderboard high on the screen.
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PlayboardTheme.colors.surface,
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            GroupAvatar(name = group.name, avatarColorHex = group.avatarColor, size = 26.dp)
            // Name + caret share one weighted slot so the caret trails the name
            // directly (rather than being pushed out to the player count).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = PlayboardTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = if (isExpanded) "▴" else "▾",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    color = PlayboardTheme.colors.textMuted,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                text = "${group.memberCount} players",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                color = PlayboardTheme.colors.textMuted,
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
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PlayboardTheme.colors.surface,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            if (showSpinner) {
                CircularProgressIndicator(color = PlayboardTheme.colors.brand, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PlayboardTheme.colors.textMuted,
                    modifier = Modifier.padding(start = 12.dp),
                )
            } else {
                val contentColor =
                    if (accent) PlayboardTheme.colors.brand else PlayboardTheme.colors.textMuted
                if (icon != null) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null, // the card's own text labels the action
                        tint = contentColor,
                        modifier = Modifier.size(20.dp).padding(end = 2.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
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
    canManageSelected: Boolean,
    onGroupSelected: (String) -> Unit,
    onCreateOrJoinGroupClicked: () -> Unit,
    onInvitePlayersClicked: () -> Unit,
    onAddMemberClicked: () -> Unit,
    onEditGroup: (groupId: String, groupName: String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PlayboardTheme.colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "YOUR GROUPS",
                style = MaterialTheme.typography.labelSmall,
                color = PlayboardTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            groups.forEach { group ->
                val isSelected = group.id == selectedGroupId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGroupSelected(group.id) }
                        .background(if (isSelected) PlayboardTheme.colors.brand.copy(alpha = 0.08f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    GroupAvatar(name = group.name, avatarColorHex = group.avatarColor, size = 36.dp)
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) PlayboardTheme.colors.brand else PlayboardTheme.colors.textPrimary,
                        )
                        Text(
                            text = "${group.memberCount} players · ${group.matchCount} matches",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                            color = PlayboardTheme.colors.textMuted,
                        )
                    }
                    // Owner/admin only — a pencil to rename the group. Its own clickable so
                    // tapping it opens the rename sheet rather than toggling the switcher.
                    if (group.canManage) {
//                        Text(
//                            text = "rename",
//                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
//                            color = PlayboardTheme.colors.textMuted,
//                            modifier = Modifier
//                                .clip(RoundedCornerShape(8.dp))
//                                .clickable { onEditGroup(group.id, group.name) }
//                                .padding(horizontal = 10.dp, vertical = 4.dp),
//                        )

                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_edit),
                            contentDescription = "Edit",
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onEditGroup(group.id, group.name) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .size(15.dp),
                            tint = PlayboardTheme.colors.brand // Retains original asset colors
                        )
                    }
                }
            }
            HorizontalDivider(
                color = PlayboardTheme.colors.textMuted.copy(alpha = 0.15f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (canInviteSelected) {
                PanelActionRow(
                    icon = R.drawable.ic_invite,
                    label = "Invite players",
                    onClick = onInvitePlayersClicked,
                )
            }
            if (canManageSelected) {
                PanelActionRow(
                    icon = R.drawable.ic_mail,
                    label = "Add member by email",
                    onClick = onAddMemberClicked,
                )
            }
            PanelActionRow(
                icon = R.drawable.ic_create,
                label = "Create or join a group",
                onClick = onCreateOrJoinGroupClicked,
            )
        }
    }
}

/** A tappable action row at the foot of the group switcher (invite, create/join). */
@Composable
private fun PanelActionRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null, // the adjacent label already names the action
            tint = PlayboardTheme.colors.brand,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = PlayboardTheme.colors.textMuted,
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
            onEditGroup = {_,_ -> },
            onGroupSelected = {},
            onCreateOrJoinGroupClicked = {},
            onInvitePlayersClicked = {},
            onAddMemberClicked = {},
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
            onEditGroup = {_,_ -> },
            onGroupSelected = {},
            onCreateOrJoinGroupClicked = {},
            onInvitePlayersClicked = {},
            onAddMemberClicked = {},
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
            onEditGroup = {_,_ -> },
            onGroupSelected = {},
            onCreateOrJoinGroupClicked = {},
            onInvitePlayersClicked = {},
            onAddMemberClicked = {},
            onRetry = {},
        )
    }
}
