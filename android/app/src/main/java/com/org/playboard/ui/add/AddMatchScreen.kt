package com.org.playboard.ui.add

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.org.playboard.data.model.Member
import com.org.playboard.data.model.UserSession
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.theme.BackgroundDark
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.OnBrandLime
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.theme.StatLossRed
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted
import com.org.playboard.ui.theme.TextPrimary

/** Add Match tab — record a doubles result (docs/requirements/04-add-match.md). */
@Composable
fun AddMatchScreen(
    onRecorded: () -> Unit,
    viewModel: AddMatchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.recorded.collect { onRecorded() }
    }

    AddMatchContent(
        state = uiState,
        onEmptySlotClicked = viewModel::onEmptySlotClicked,
        onRemovePlayer = viewModel::onRemovePlayer,
        onSetScoreChanged = viewModel::onSetScoreChanged,
        onAddSet = viewModel::onAddSet,
        onRemoveSet = viewModel::onRemoveSet,
        onWinnerSelected = viewModel::onWinnerSelected,
        onRecord = viewModel::onRecord,
        onRetry = viewModel::retry,
    )

    if (uiState.playerPickerTeam != null) {
        PlayerPickerSheet(
            available = uiState.availablePlayers,
            onPick = viewModel::onPlayerPicked,
            onDismiss = viewModel::onPlayerPickerDismissed,
        )
    }
}

@Composable
private fun AddMatchContent(
    state: AddMatchUiState,
    onEmptySlotClicked: (Int) -> Unit,
    onRemovePlayer: (String) -> Unit,
    onSetScoreChanged: (Int, Int, String) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onWinnerSelected: (Int) -> Unit,
    onRecord: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.isLoading -> CenteredBox { CircularProgressIndicator(color = BrandLime) }
        state.noGroup -> CenteredMessage("Create or join a group before recording a match.")
        state.hasLoadFailed -> CenteredBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load the roster.", color = TextMuted)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandLime, contentColor = OnBrandLime),
                ) { Text("Retry") }
            }
        }
        else -> AddMatchForm(
            state = state,
            onEmptySlotClicked = onEmptySlotClicked,
            onRemovePlayer = onRemovePlayer,
            onSetScoreChanged = onSetScoreChanged,
            onAddSet = onAddSet,
            onRemoveSet = onRemoveSet,
            onWinnerSelected = onWinnerSelected,
            onRecord = onRecord,
        )
    }
}

@Composable
private fun AddMatchForm(
    state: AddMatchUiState,
    onEmptySlotClicked: (Int) -> Unit,
    onRemovePlayer: (String) -> Unit,
    onSetScoreChanged: (Int, Int, String) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onWinnerSelected: (Int) -> Unit,
    onRecord: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Header(groupName = state.groupName, recorder = state.recorder)

        SectionLabel("BUILD TEAMS")
        TeamSlots(
            teamNo = 1,
            players = state.teamPlayers(1),
            onEmptySlotClicked = onEmptySlotClicked,
            onRemovePlayer = onRemovePlayer,
        )
        Spacer(Modifier.height(12.dp))
        TeamSlots(
            teamNo = 2,
            players = state.teamPlayers(2),
            onEmptySlotClicked = onEmptySlotClicked,
            onRemovePlayer = onRemovePlayer,
        )

        Spacer(Modifier.height(28.dp))
        SectionLabel("SCORE BY SET")
        state.sets.forEachIndexed { index, set ->
            SetRow(
                index = index,
                team1 = set.team1,
                team2 = set.team2,
                canRemove = state.sets.size > 1,
                onScoreChanged = onSetScoreChanged,
                onRemove = onRemoveSet,
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = "+ Add set",
            color = BrandLime,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAddSet)
                .padding(vertical = 6.dp, horizontal = 4.dp),
        )

        Spacer(Modifier.height(28.dp))
        SectionLabel("WHO WON?")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            WinnerCard(
                teamNo = 1,
                players = state.teamPlayers(1),
                selected = state.effectiveWinner == 1,
                onClick = { onWinnerSelected(1) },
                modifier = Modifier.weight(1f),
            )
            WinnerCard(
                teamNo = 2,
                players = state.teamPlayers(2),
                selected = state.effectiveWinner == 2,
                onClick = { onWinnerSelected(2) },
                modifier = Modifier.weight(1f),
            )
        }

        if (state.submitError != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = errorMessage(state.submitError),
                color = StatLossRed,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRecord,
            enabled = state.canRecord,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandLime,
                contentColor = OnBrandLime,
                disabledContainerColor = BrandLime.copy(alpha = 0.35f),
                disabledContentColor = OnBrandLime.copy(alpha = 0.6f),
            ),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(color = OnBrandLime, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Text("Record Match", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Header(groupName: String?, recorder: UserSession?) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            text = "ADD MATCH",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp, lineHeight = 28.sp),
            color = BrandLime,
        )
        if (groupName != null) {
            Text(text = groupName, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        if (recorder != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Recording as ${recorder.displayName} · ${recorder.email}",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
    )
}

@Composable
private fun TeamSlots(
    teamNo: Int,
    players: List<Member>,
    onEmptySlotClicked: (Int) -> Unit,
    onRemovePlayer: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Team $teamNo",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.width(72.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(AddMatchUiState.TEAM_SIZE) { i ->
                val member = players.getOrNull(i)
                if (member != null) {
                    FilledSlot(member = member, onRemove = { onRemovePlayer(member.id) })
                } else {
                    EmptySlot(onClick = { onEmptySlotClicked(teamNo) })
                }
            }
        }
    }
}

@Composable
private fun FilledSlot(member: Member, onRemove: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            PlayerAvatar(
                displayName = member.displayName,
                photoUrl = member.photoUrl,
                avatarColorHex = member.avatarColor,
                size = 52.dp,
            )
            // A small × badge to remove the player (tapping the slot also removes).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.dp, TextMuted.copy(alpha = 0.5f), CircleShape)
                    .clickable(onClick = onRemove),
            ) {
                Text("×", color = TextPrimary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = member.displayName,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
            color = TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptySlot(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(1.dp, TextMuted.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onClick),
        ) {
            Text("+", color = TextMuted, fontSize = 22.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(text = " ", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp))
    }
}

@Composable
private fun SetRow(
    index: Int,
    team1: String,
    team2: String,
    canRemove: Boolean,
    onScoreChanged: (Int, Int, String) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Set ${index + 1}",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.width(64.dp),
        )
        ScoreField(value = team1, onChange = { onScoreChanged(index, 1, it) })
        Text(
            text = "–",
            color = TextMuted,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        ScoreField(value = team2, onChange = { onScoreChanged(index, 2, it) })
        Spacer(Modifier.weight(1f))
        if (canRemove) {
            Text(
                text = "×",
                color = TextMuted,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onRemove(index) }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ScoreField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("0", color = TextMuted.copy(alpha = 0.5f), textAlign = TextAlign.Center) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandLime,
            unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
            cursorColor = BrandLime,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = BackgroundDark,
            unfocusedContainerColor = BackgroundDark,
        ),
        modifier = Modifier.width(72.dp),
    )
}

@Composable
private fun WinnerCard(
    teamNo: Int,
    players: List<Member>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = if (players.size == AddMatchUiState.TEAM_SIZE) {
        players.joinToString(" & ") { it.displayName }
    } else {
        "? & ?"
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) BrandLime.copy(alpha = 0.12f) else SurfaceDark)
            .border(
                1.dp,
                if (selected) BrandLime else TextMuted.copy(alpha = 0.2f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
    ) {
        Text(
            text = "Team $teamNo",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) BrandLime else TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = names,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
            color = TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CenteredMessage(text: String) {
    CenteredBox {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

private fun errorMessage(error: RecordMatchError): String = when (error) {
    RecordMatchError.INVALID_SCORES -> "Those scores aren't valid for a badminton set. Check and try again."
    RecordMatchError.INVALID_TEAMS -> "Team lineup isn't valid. Each team needs two different players."
    RecordMatchError.NETWORK -> "Couldn't record the match. Please try again."
}

private val previewRoster = listOf(
    Member("u1", "Raj", null, "#9ADE28", "owner"),
    Member("u2", "Dev", null, "#3DB4FF", "member"),
    Member("u3", "Marcus", null, "#FF8A3D", "member"),
    Member("u4", "Kiran", null, "#EAC72B", "member"),
)

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, heightDp = 1200)
@Composable
private fun AddMatchFormPreview() {
    PlayboardTheme {
        AddMatchForm(
            state = AddMatchUiState(
                isLoading = false,
                groupId = "g1",
                groupName = "Saturday Smashers",
                recorder = UserSession("u1", "Raj", "raj@example.com", null, "#9ADE28"),
                roster = previewRoster,
                team1 = listOf("u1", "u2"),
                team2 = listOf("u3"),
                sets = listOf(SetScoreInput("21", "12"), SetScoreInput("21", "17")),
            ),
            onEmptySlotClicked = {},
            onRemovePlayer = {},
            onSetScoreChanged = { _, _, _ -> },
            onAddSet = {},
            onRemoveSet = {},
            onWinnerSelected = {},
            onRecord = {},
        )
    }
}
