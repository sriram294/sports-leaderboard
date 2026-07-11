package com.org.playboard.ui.matches

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchDetail
import com.org.playboard.data.model.MatchEvent
import com.org.playboard.data.model.MatchPlayer
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.MatchTeam
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.theme.BackgroundDark
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.OnBrandLime
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.theme.StatLossRed
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted
import com.org.playboard.ui.theme.TextPrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Matches tab — chronological match log (docs/requirements/03-matches.md). */
@Composable
fun MatchesScreen(
    onEditMatch: (String) -> Unit,
    viewModel: MatchesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    MatchesContent(
        state = uiState,
        onMatchClicked = viewModel::onMatchClicked,
        onEditClicked = onEditMatch,
        onDeleteClicked = viewModel::onDeleteClicked,
        onRetry = viewModel::retry,
    )

    if (uiState.deleteTargetId != null) {
        DeleteConfirmDialog(
            isDeleting = uiState.isDeleting,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismissed,
        )
    }
}

@Composable
private fun MatchesContent(
    state: MatchesUiState,
    onMatchClicked: (String) -> Unit,
    onEditClicked: (String) -> Unit,
    onDeleteClicked: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        when {
            state.isLoading -> CenteredBox { CircularProgressIndicator(color = BrandLime) }
            state.noGroup -> CenteredMessage("Create or join a group to see its matches.")
            state.hasLoadFailed -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load matches.", color = TextMuted)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onRetry) { Text("Retry", color = BrandLime) }
                }
            }
            state.matches.isEmpty() -> CenteredMessage("No matches recorded yet.\nRecord one from the + tab.")
            else -> MatchList(
                state = state,
                onMatchClicked = onMatchClicked,
                onEditClicked = onEditClicked,
                onDeleteClicked = onDeleteClicked,
            )
        }
    }
}

@Composable
private fun MatchList(
    state: MatchesUiState,
    onMatchClicked: (String) -> Unit,
    onEditClicked: (String) -> Unit,
    onDeleteClicked: (String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = "${state.matchCount} doubles ${if (state.matchCount == 1) "match" else "matches"} · tap to expand",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
        state.sections.forEach { section ->
            item(key = "date-${section.date}") {
                Text(
                    text = "${dateLabel(section.date)} · ${section.matches.size} ${if (section.matches.size == 1) "match" else "matches"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
            }
            items(section.matches, key = { it.id }) { match ->
                MatchCard(
                    match = match,
                    isExpanded = state.expandedId == match.id,
                    isDetailLoading = state.expandedId == match.id && state.isDetailLoading,
                    detailFailed = state.expandedId == match.id && state.detailFailed,
                    detail = if (state.expandedId == match.id) state.detail else null,
                    canModify = state.detail?.let { state.expandedId == match.id && state.canModify(it) } ?: false,
                    onClick = { onMatchClicked(match.id) },
                    onEdit = { onEditClicked(match.id) },
                    onDelete = { onDeleteClicked(match.id) },
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MatchCard(
    match: Match,
    isExpanded: Boolean,
    isDetailLoading: Boolean,
    detailFailed: Boolean,
    detail: MatchDetail?,
    canModify: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBlock(team = match.team(1), isWinner = match.winningTeamNo == 1, modifier = Modifier.weight(1f))
                ScoreColumn(sets = match.sets)
                TeamBlock(
                    team = match.team(2),
                    isWinner = match.winningTeamNo == 2,
                    alignEnd = true,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (isExpanded) "▴" else "▾",
                    color = TextMuted,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            if (isExpanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))
                when {
                    isDetailLoading -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandLime, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                    detailFailed -> Text("Couldn't load match details.", color = TextMuted)
                    detail != null -> ExpandedDetail(detail = detail, canModify = canModify, onEdit = onEdit, onDelete = onDelete)
                }
            }
        }
    }
}

@Composable
private fun TeamBlock(team: MatchTeam?, isWinner: Boolean, alignEnd: Boolean = false, modifier: Modifier = Modifier) {
    val players = team?.players.orEmpty()
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (alignEnd && isWinner) WinnerBadge()
            players.forEach { p ->
                PlayerAvatar(displayName = p.displayName, photoUrl = p.photoUrl, avatarColorHex = p.avatarColor, size = 30.dp)
            }
            if (!alignEnd && isWinner) WinnerBadge()
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = players.joinToString(" & ") { it.displayName },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (isWinner) BrandLime else TextPrimary,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            maxLines = 2,
        )
    }
}

@Composable
private fun WinnerBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(20.dp).clip(CircleShape).background(BrandLime),
    ) {
        Text("W", color = OnBrandLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScoreColumn(sets: List<MatchSet>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 10.dp)) {
        sets.forEach { set ->
            Text(
                text = "${set.team1Score} – ${set.team2Score}",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }
    }
}

@Composable
private fun ExpandedDetail(detail: MatchDetail, canModify: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column {
        SubLabel("GAME BREAKDOWN")
        detail.sets.forEach { set ->
            Text(
                text = "Set ${set.setNo}:  ${set.team1Score} – ${set.team2Score}",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = TextPrimary,
            )
        }
        val winner = detail.winningTeamNo?.let { detail.team(it) }?.players?.joinToString(" & ") { it.displayName }
        if (winner != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Winner: $winner",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.SemiBold,
                color = BrandLime,
            )
        }

        Spacer(Modifier.height(12.dp))
        SubLabel("HISTORY")
        detail.events.forEach { event ->
            Text(
                text = "${event.displayName} · ${actionLabel(event.action)} · ${timeLabel(event.createdAt)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                color = TextMuted,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }

        if (canModify) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineActionButton(label = "Edit match", color = BrandLime, onClick = onEdit)
                OutlineActionButton(label = "Delete match", color = StatLossRed, onClick = onDelete)
            }
        }
    }
}

/** A pill-outlined text button used for the edit/delete actions on an expanded match. */
@Composable
private fun OutlineActionButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DeleteConfirmDialog(isDeleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("Delete match?", color = TextPrimary) },
        text = {
            Text(
                "This permanently removes the match and updates the leaderboard.",
                color = TextMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isDeleting) {
                Text(if (isDeleting) "Deleting…" else "Delete", color = StatLossRed, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel", color = TextMuted) }
        },
    )
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
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

private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
private val timeFormatter = DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.getDefault())

private fun dateLabel(date: java.time.LocalDate): String = date.format(dateFormatter)

private fun timeLabel(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).format(timeFormatter)

private fun actionLabel(action: String): String = when (action.lowercase(Locale.ROOT)) {
    "created" -> "Recorded this match"
    "edited", "updated" -> "Edited this match"
    else -> action
}

private val previewMatch = Match(
    id = "m1",
    playedAt = Instant.parse("2026-07-09T06:58:00Z"),
    teams = listOf(
        MatchTeam(1, true, listOf(MatchPlayer("u1", "Raj", "#9ADE28", null), MatchPlayer("u2", "Dev", "#3DB4FF", null))),
        MatchTeam(2, false, listOf(MatchPlayer("u3", "Marcus", "#FF8A3D", null), MatchPlayer("u4", "Kiran", "#EAC72B", null))),
    ),
    sets = listOf(MatchSet(1, 21, 12), MatchSet(2, 21, 17)),
)

private val previewDetail = MatchDetail(
    id = "m1",
    playedAt = previewMatch.playedAt,
    teams = previewMatch.teams,
    sets = previewMatch.sets,
    recordedByUserId = "u1",
    recordedByName = "Raj",
    recordedAt = previewMatch.playedAt,
    events = listOf(MatchEvent("Raj", "created", previewMatch.playedAt)),
)

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, heightDp = 700)
@Composable
private fun MatchesContentPreview() {
    PlayboardTheme {
        MatchesContent(
            state = MatchesUiState(
                isLoading = false,
                groupId = "g1",
                groupName = "Saturday Smashers",
                currentUserId = "u1",
                matches = listOf(previewMatch),
                expandedId = "m1",
                detail = previewDetail,
            ),
            onMatchClicked = {},
            onEditClicked = {},
            onDeleteClicked = {},
            onRetry = {},
        )
    }
}
