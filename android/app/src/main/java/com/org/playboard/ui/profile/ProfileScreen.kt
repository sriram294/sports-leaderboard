package com.org.playboard.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.org.playboard.data.model.BestPartner
import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchPlayer
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.MatchTeam
import com.org.playboard.data.model.PlayerStats
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.components.avatarColor
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

/** Profile tab — account info + per-group stats (docs/requirements/05-profile.md). */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    ProfileContent(
        state = uiState,
        onSignOut = viewModel::onSignOutClicked,
        onRetry = viewModel::retry,
    )
}

@Composable
private fun ProfileContent(state: ProfileUiState, onSignOut: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        when {
            state.isLoading -> CenteredBox { CircularProgressIndicator(color = BrandLime) }
            state.noGroup -> CenteredMessage("Create or join a group to see your stats.")
            state.hasLoadFailed -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load your stats.", color = TextMuted)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onRetry) { Text("Retry", color = BrandLime) }
                }
            }
            state.stats != null -> StatsList(state = state, stats = state.stats, onSignOut = onSignOut)
        }
    }
}

@Composable
private fun StatsList(state: ProfileUiState, stats: PlayerStats, onSignOut: () -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.isOwnProfile) {
            item { AccountRow(email = state.email, onSignOut = onSignOut) }
        }
        item { IdentityCard(stats = stats) }
        item { StatTilesGrid(stats = stats) }
        stats.bestPartner?.let { partner ->
            item { BestPartnerCard(partner = partner) }
        }
        if (state.recentMatches.isNotEmpty()) {
            item { SectionLabel("RECENT MATCHES") }
            items(state.recentMatches, key = { it.matchId }) { row -> RecentMatchRowCard(row = row) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AccountRow(email: String?, onSignOut: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceDark, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(TextPrimary),
            ) {
                Text("G", color = SurfaceDark, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Signed in with Google",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    email ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, TextMuted.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onSignOut)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Sign out", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun IdentityCard(stats: PlayerStats) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceDark,
        border = BorderStroke(1.5.dp, BrandLime),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp),
        ) {
            PlayerAvatar(
                displayName = stats.displayName,
                photoUrl = stats.photoUrl,
                avatarColorHex = stats.avatarColor,
                size = 76.dp,
            )
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    text = stats.displayName.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp, lineHeight = 28.sp),
                    color = BrandLime,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${stats.matchesPlayed} ${if (stats.matchesPlayed == 1) "match" else "matches"} played",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = TextMuted,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${stats.winRatePercent}%",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, lineHeight = 28.sp),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "WIN RATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTilesGrid(stats: PlayerStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // IntrinsicSize.Min + fillMaxHeight makes every tile in a row match the
        // tallest one, so a sub-label (e.g. "Best: N") can't leave them uneven.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            StatTile("WINS", stats.wins.toString(), modifier = Modifier.weight(1f).fillMaxHeight())
            StatTile("LOSSES", stats.losses.toString(), modifier = Modifier.weight(1f).fillMaxHeight())
            StatTile("PTS FOR", stats.pointsFor.toString(), modifier = Modifier.weight(1f).fillMaxHeight())
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            StatTile(
                "STREAK",
                stats.currentStreak.toString(),
                valueColor = BrandLime,
                subLabel = "Best: ${stats.bestStreak}",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile("BEST STREAK", stats.bestStreak.toString(), modifier = Modifier.weight(1f).fillMaxHeight())
            StatTile("PTS AGNST", stats.pointsAgainst.toString(), modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    subLabel: String? = null,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceDark, modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp, lineHeight = 28.sp),
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
            Spacer(Modifier.height(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun BestPartnerCard(partner: BestPartner) {
    val accent = avatarColor(partner.avatarColor)
    Column {
        SectionLabel("BEST PARTNER")
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceDark, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
            ) {
                PlayerAvatar(
                    displayName = partner.displayName,
                    photoUrl = partner.photoUrl,
                    avatarColorHex = partner.avatarColor,
                    size = 44.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = partner.displayName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "${partner.winsTogether}W / ${partner.gamesTogether} games together",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
                Text(
                    text = "${partner.winRatePercent}%",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 22.sp, lineHeight = 24.sp),
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
        }
    }
}

@Composable
private fun RecentMatchRowCard(row: RecentMatchRow) {
    val accent = if (row.isWin) BrandLime else StatLossRed
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceDark, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ResultBadge(isWin = row.isWin)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = dateLabel(row.playedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        if (row.partnerNames.isNotBlank()) append("w/ ${row.partnerNames} ")
                        append("vs ${row.opponentNames}")
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = TextPrimary,
                )
                if (row.sets.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = row.sets.joinToString(", ") { "${it.team1Score}-${it.team2Score}" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                        color = BrandLime,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultBadge(isWin: Boolean) {
    val color = if (isWin) BrandLime else StatLossRed
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (isWin) "WIN" else "LOSS",
            color = if (isWin) OnBrandLime else TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
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

private fun dateLabel(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)

private val previewStats = PlayerStats(
    userId = "u1",
    displayName = "Raj",
    photoUrl = null,
    avatarColor = "#9ADE28",
    matchesPlayed = 8,
    wins = 4,
    losses = 4,
    pointsFor = 315,
    pointsAgainst = 320,
    winRate = 0.5,
    currentStreak = 2,
    bestStreak = 2,
    bestPartner = BestPartner("u2", "Dev", null, "#3DB4FF", gamesTogether = 2, winsTogether = 2, winRate = 1.0),
    recentMatches = listOf(
        Match(
            id = "m1",
            playedAt = Instant.parse("2026-07-09T06:58:00Z"),
            teams = listOf(
                MatchTeam(1, true, listOf(MatchPlayer("u1", "Raj", "#9ADE28", null), MatchPlayer("u2", "Dev", "#3DB4FF", null))),
                MatchTeam(2, false, listOf(MatchPlayer("u3", "Marcus", "#FF8A3D", null), MatchPlayer("u4", "Kiran", "#EAC72B", null))),
            ),
            sets = listOf(MatchSet(1, 21, 12), MatchSet(2, 21, 17)),
        ),
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, heightDp = 1100)
@Composable
private fun ProfileContentPreview() {
    PlayboardTheme {
        ProfileContent(
            state = ProfileUiState(
                isLoading = false,
                groupName = "Saturday Smashers",
                email = "raj@gmail.com",
                stats = previewStats,
            ),
            onSignOut = {},
            onRetry = {},
        )
    }
}
