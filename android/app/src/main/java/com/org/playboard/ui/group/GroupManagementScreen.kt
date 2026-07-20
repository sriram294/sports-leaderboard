package com.org.playboard.ui.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.org.playboard.data.model.Group
import com.org.playboard.data.model.Member
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.theme.PlayboardTheme
import java.util.Locale

/**
 * Group-management drill-down (owner/admin): a list of the groups the user manages → per-group
 * member management (remove, role changes, add/invite) + the daily session window. Self-contained
 * list↔detail navigation with its own back handling; [onExit] leaves the drill-down entirely.
 */
@Composable
fun GroupManagementScreen(onExit: () -> Unit, viewModel: GroupManagementViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(enabled = true) {
        if (state.selectedGroupId != null) viewModel.onBackToList() else onExit()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
    ) {
        val group = state.selectedGroup
        if (group == null) {
            BackRow(label = "Profile", onBack = onExit)
            GroupList(state = state, onSelect = viewModel::onSelectGroup)
        } else {
            BackRow(label = "Groups", onBack = viewModel::onBackToList)
            GroupDetail(
                state = state,
                group = group,
                onRemove = viewModel::onRemoveMember,
                onChangeRole = viewModel::onChangeRole,
                onAddMember = viewModel::onAddMember,
                onCreateInvite = viewModel::onCreateInvite,
                onSetSession = viewModel::onSetSession,
                onRetryMembers = viewModel::retryMembers,
            )
        }
    }

    state.inviteCode?.let { code -> InviteCodeDialog(code = code, onDismiss = viewModel::onInviteDismissed) }
    state.actionError?.let { message -> ErrorDialog(message = message, onDismiss = viewModel::onErrorDismissed) }
}

// ─────────────────────────────── List ───────────────────────────────

@Composable
private fun GroupList(state: GroupManagementUiState, onSelect: (String) -> Unit) {
    if (state.managedGroups.isEmpty()) {
        CenteredMessage("You don't manage any groups.\nOnly a group's owner or admins can manage it.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item { SectionLabel("GROUPS YOU MANAGE") }
        items(state.managedGroups, key = { it.id }) { group ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PlayboardTheme.colors.surface,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSelect(group.id) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    PlayerAvatar(displayName = group.name, photoUrl = null, avatarId = null, avatarColorHex = group.avatarColor, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.name, fontWeight = FontWeight.SemiBold, color = PlayboardTheme.colors.textPrimary)
                        Text(
                            "${group.memberCount} ${if (group.memberCount == 1) "member" else "members"} · ${group.matchCount} matches",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlayboardTheme.colors.textMuted,
                        )
                    }
                    RoleBadge(group.myRole)
                    Text("›", color = PlayboardTheme.colors.textMuted, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

// ─────────────────────────────── Detail ───────────────────────────────

@Composable
private fun GroupDetail(
    state: GroupManagementUiState,
    group: Group,
    onRemove: (String) -> Unit,
    onChangeRole: (String, String) -> Unit,
    onAddMember: (String, String) -> Unit,
    onCreateInvite: () -> Unit,
    onSetSession: (String?, String?) -> Unit,
    onRetryMembers: () -> Unit,
) {
    var removeTarget by remember { mutableStateOf<Member?>(null) }
    var showAddMember by remember { mutableStateOf(false) }
    var showSessionDialog by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                group.name,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp, lineHeight = 28.sp),
                fontWeight = FontWeight.Bold,
                color = PlayboardTheme.colors.textPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // ── Session window ──
        item {
            Column {
                SectionLabel("SESSION TIME")
                Surface(shape = RoundedCornerShape(16.dp), color = PlayboardTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            val hasWindow = group.sessionStart != null && group.sessionEnd != null
                            Text(
                                text = if (hasWindow) "${group.sessionStart} – ${group.sessionEnd}" else "No session time set",
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasWindow) PlayboardTheme.colors.textPrimary else PlayboardTheme.colors.textMuted,
                            )
                            Text(
                                "Daily playing time — used for reminders later.",
                                style = MaterialTheme.typography.labelSmall,
                                color = PlayboardTheme.colors.textMuted,
                            )
                        }
                        PillButton("Edit", enabled = !state.busy) { showSessionDialog = true }
                    }
                }
            }
        }

        // ── Members ──
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text("MEMBERS", style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted, modifier = Modifier.weight(1f))
                PillButton("Invite", enabled = !state.busy, onClick = onCreateInvite)
                Spacer(Modifier.width(8.dp))
                PillButton("Add", enabled = !state.busy) { showAddMember = true }
            }
        }
        when {
            state.isLoadingMembers && state.members.isEmpty() ->
                item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PlayboardTheme.colors.brand, strokeWidth = 2.dp, modifier = Modifier.width(24.dp).height(24.dp)) } }
            state.membersFailed ->
                item { TextButton(onClick = onRetryMembers) { Text("Couldn't load members. Retry", color = PlayboardTheme.colors.brand) } }
            else -> items(state.members, key = { it.id }) { member ->
                MemberRow(
                    member = member,
                    isSelf = member.id == state.currentUserId,
                    canRemove = state.canRemove(group, member),
                    canChangeRole = state.canChangeRoles(group) && member.role != "owner" && !member.isGuest && member.id != state.currentUserId,
                    busy = state.busy,
                    onRemove = { removeTarget = member },
                    onChangeRole = { onChangeRole(member.id, if (member.role == "admin") "member" else "admin") },
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    removeTarget?.let { target ->
        ConfirmDialog(
            title = "Remove ${target.displayName}?",
            body = "They'll drop off this group's roster and leaderboard. Their recorded matches stay.",
            confirmLabel = "Remove",
            destructive = true,
            busy = state.busy,
            onConfirm = { onRemove(target.id); removeTarget = null },
            onDismiss = { removeTarget = null },
        )
    }
    if (showAddMember) {
        AddMemberDialog(busy = state.busy, onAdd = { email, name -> onAddMember(email, name); showAddMember = false }, onDismiss = { showAddMember = false })
    }
    if (showSessionDialog) {
        SessionDialog(
            initialStart = group.sessionStart,
            initialEnd = group.sessionEnd,
            onSave = { start, end -> onSetSession(start, end); showSessionDialog = false },
            onDismiss = { showSessionDialog = false },
        )
    }
}

@Composable
private fun MemberRow(
    member: Member,
    isSelf: Boolean,
    canRemove: Boolean,
    canChangeRole: Boolean,
    busy: Boolean,
    onRemove: () -> Unit,
    onChangeRole: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = PlayboardTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            PlayerAvatar(displayName = member.displayName, photoUrl = member.photoUrl, avatarId = member.avatarId, avatarColorHex = member.avatarColor, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSelf) "${member.displayName} (you)" else member.displayName,
                    fontWeight = FontWeight.SemiBold,
                    color = PlayboardTheme.colors.textPrimary,
                )
                RoleBadge(member.role)
            }
            if (canChangeRole) {
                PillButton(if (member.role == "admin") "Demote" else "Make admin", enabled = !busy, onClick = onChangeRole)
                Spacer(Modifier.width(8.dp))
            }
            if (canRemove) {
                PillButton("Remove", enabled = !busy, color = PlayboardTheme.colors.statLoss, onClick = onRemove)
            }
        }
    }
}

// ─────────────────────────────── Dialogs ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDialog(initialStart: String?, initialEnd: String?, onSave: (String?, String?) -> Unit, onDismiss: () -> Unit) {
    val startState = rememberTimePickerState(initialHour = hourOf(initialStart, 19), initialMinute = minuteOf(initialStart, 0), is24Hour = true)
    val endState = rememberTimePickerState(initialHour = hourOf(initialEnd, 21), initialMinute = minuteOf(initialEnd, 0), is24Hour = true)
    val startMinutes = startState.hour * 60 + startState.minute
    val endMinutes = endState.hour * 60 + endState.minute
    val valid = startMinutes < endMinutes

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlayboardTheme.colors.surface,
        title = { Text("Session time", color = PlayboardTheme.colors.textPrimary) },
        text = {
            Column {
                Text("Start", style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted)
                TimeInput(state = startState)
                Spacer(Modifier.height(8.dp))
                Text("End", style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted)
                TimeInput(state = endState)
                if (!valid) {
                    Text("Start must be before end.", color = PlayboardTheme.colors.statLoss, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(formatTime(startState.hour, startState.minute), formatTime(endState.hour, endState.minute)) },
            ) { Text("Save", color = if (valid) PlayboardTheme.colors.brand else PlayboardTheme.colors.textMuted, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row {
                if (initialStart != null) {
                    TextButton(onClick = { onSave(null, null) }) { Text("Clear", color = PlayboardTheme.colors.textMuted) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = PlayboardTheme.colors.textMuted) }
            }
        },
    )
}

@Composable
private fun AddMemberDialog(busy: Boolean, onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && name.isNotBlank() && !busy
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlayboardTheme.colors.surface,
        title = { Text("Add member", color = PlayboardTheme.colors.textPrimary) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = canSubmit, onClick = { onAdd(email.trim(), name.trim()) }) {
                Text("Add", color = if (canSubmit) PlayboardTheme.colors.brand else PlayboardTheme.colors.textMuted, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PlayboardTheme.colors.textMuted) } },
    )
}

@Composable
private fun InviteCodeDialog(code: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlayboardTheme.colors.surface,
        title = { Text("Invite code", color = PlayboardTheme.colors.textPrimary) },
        text = {
            Column {
                Text("Share this code — anyone can join with it:", color = PlayboardTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                Text(code, style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp), fontWeight = FontWeight.Bold, color = PlayboardTheme.colors.brand)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = PlayboardTheme.colors.brand) } },
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirmLabel: String, destructive: Boolean, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlayboardTheme.colors.surface,
        title = { Text(title, color = PlayboardTheme.colors.textPrimary) },
        text = { Text(body, color = PlayboardTheme.colors.textMuted) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) PlayboardTheme.colors.statLoss else PlayboardTheme.colors.brand, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PlayboardTheme.colors.textMuted) } },
    )
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlayboardTheme.colors.surface,
        text = { Text(message, color = PlayboardTheme.colors.textPrimary) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = PlayboardTheme.colors.brand) } },
    )
}

// ─────────────────────────────── Shared bits ───────────────────────────────

@Composable
private fun RoleBadge(role: String) {
    val isOwner = role == "owner"
    val color = if (isOwner || role == "admin") PlayboardTheme.colors.brand else PlayboardTheme.colors.textMuted
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(role.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = color)
    }
}

@Composable
private fun PillButton(label: String, enabled: Boolean = true, color: androidx.compose.ui.graphics.Color = PlayboardTheme.colors.brand, onClick: () -> Unit) {
    val tint = if (enabled) color else color.copy(alpha = 0.4f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BackRow(label: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Text("←", color = PlayboardTheme.colors.brand, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = PlayboardTheme.colors.brand, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = PlayboardTheme.colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PlayboardTheme.colors.brand,
    unfocusedBorderColor = PlayboardTheme.colors.textMuted.copy(alpha = 0.5f),
    focusedTextColor = PlayboardTheme.colors.textPrimary,
    unfocusedTextColor = PlayboardTheme.colors.textPrimary,
    cursorColor = PlayboardTheme.colors.brand,
    focusedLabelColor = PlayboardTheme.colors.brand,
    unfocusedLabelColor = PlayboardTheme.colors.textMuted,
)

private fun hourOf(time: String?, default: Int): Int = time?.substringBefore(":")?.toIntOrNull() ?: default

private fun minuteOf(time: String?, default: Int): Int = time?.substringAfter(":")?.toIntOrNull() ?: default

private fun formatTime(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
