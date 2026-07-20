package com.org.playboard.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.org.playboard.R
import com.org.playboard.data.model.BestPartner
import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchPlayer
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.MatchTeam
import com.org.playboard.data.model.PlayerStats
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.components.avatarColor
import com.org.playboard.ui.theme.PlayboardTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Profile screen — account info + per-group stats (docs/requirements/05-profile.md).
 * Serves the Profile tab (own stats; [viewedUserId] null, no [onBack]) and the
 * Board leaderboard drill-down (another player; [viewedUserId] set, [onBack] shown).
 */
@Composable
fun ProfileScreen(
    viewedUserId: String? = null,
    onBack: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
    onOpenSettings: (() -> Unit)? = null,
    onOpenGroupManagement: (() -> Unit)? = null,
) {
    // Drive whose profile the shared ViewModel loads whenever we (re)enter with a
    // new target; setting null when already own is suppressed downstream.
    LaunchedEffect(viewedUserId) { viewModel.setViewedUser(viewedUserId) }
    val uiState by viewModel.uiState.collectAsState()
    ProfileContent(
        state = uiState,
        onRetry = viewModel::retry,
        onBack = onBack,
        onEditName = viewModel::onEditNameClicked,
        onPhotoSelected = viewModel::onPhotoSelected,
        onAvatarSelected = viewModel::onAvatarSelected,
        onRenameInputChanged = viewModel::onRenameInputChanged,
        onRenameSubmit = viewModel::onRenameSubmitted,
        onRenameDismiss = viewModel::onRenameDismissed,
        onOpenSettings = onOpenSettings,
        onOpenGroupManagement = onOpenGroupManagement,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onRetry: () -> Unit,
    onBack: (() -> Unit)? = null,
    onEditName: () -> Unit = {},
    onPhotoSelected: (ByteArray, String) -> Unit = { _, _ -> },
    onAvatarSelected: (String) -> Unit = {},
    onRenameInputChanged: (String) -> Unit = {},
    onRenameSubmit: () -> Unit = {},
    onRenameDismiss: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    onOpenGroupManagement: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Modern photo picker — no storage permission needed. Reads the picked image's
    // bytes off the main thread, then hands them to the ViewModel to upload.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                        bytes?.let { it to mime }
                    }.getOrNull()
                }
                payload?.let { (bytes, mime) -> onPhotoSelected(bytes, mime) }
            }
        }
    }
    val pickPhoto = {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    // The 25 bundled default avatars, discovered from assets/avatars so the id
    // list never drifts from the shipped files. Sorted for a stable order.
    val avatarIds = remember {
        runCatching { context.assets.list("avatars") }.getOrNull()
            ?.map { it.removeSuffix(".png") }
            ?.sortedBy { it.removePrefix("avatar").toIntOrNull() ?: 0 }
            .orEmpty()
    }
    var showAvatarSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp),
    ) {
        // Drill-down back affordance (leaderboard → player); absent on the Profile tab.
        if (onBack != null) {
            BackRow(onBack = onBack)
        }
        if (state.isOwnProfile && onBack == null && (onOpenSettings != null || onOpenGroupManagement != null)) {
            Row(
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (onOpenSettings != null) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "Settings",
                            tint = PlayboardTheme.colors.textPrimary,
                        )
                    }
                }
                if (onOpenGroupManagement != null) {
                    IconButton(onClick = onOpenGroupManagement) {
                        Icon(
                            painter = painterResource(R.drawable.ic_group),
                            contentDescription = "Manage groups",
                            tint = PlayboardTheme.colors.textPrimary,
                        )
                    }
                }
            }
        }
        when {
            state.isLoading -> CenteredBox { CircularProgressIndicator(color = PlayboardTheme.colors.brand) }
            state.noGroup -> CenteredMessage("Create or join a group to see your stats.")
            state.hasLoadFailed -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load your stats.", color = PlayboardTheme.colors.textMuted)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onRetry) { Text("Retry", color = PlayboardTheme.colors.brand) }
                }
            }
            state.stats != null -> StatsList(
                state = state,
                stats = state.stats,
                onEditName = onEditName,
                onEditAvatar = { showAvatarSheet = true },
            )
        }
    }

    if (showAvatarSheet) {
        AvatarPickerSheet(
            avatarIds = avatarIds,
            onPickAvatar = { id ->
                showAvatarSheet = false
                onAvatarSelected(id)
            },
            onUploadPhoto = {
                showAvatarSheet = false
                pickPhoto()
            },
            onDismiss = { showAvatarSheet = false },
        )
    }

    state.renameSheet?.let { sheet ->
        EditNameSheet(
            state = sheet,
            onInputChanged = onRenameInputChanged,
            onSubmit = onRenameSubmit,
            onDismiss = onRenameDismiss,
        )
    }
}

@Composable
private fun StatsList(
    state: ProfileUiState,
    stats: PlayerStats,
    onEditName: () -> Unit,
    onEditAvatar: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            ProfileHero(
                stats = stats,
                displayName = state.displayName ?: stats.displayName,
                photoUrl = state.identityPhotoUrl,
                avatarId = state.identityAvatarId,
                editable = state.isOwnProfile,
                isUploadingPhoto = state.isUploadingPhoto,
                onEditName = onEditName,
                onEditAvatar = onEditAvatar,
            )
        }
        state.updateError?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    color = PlayboardTheme.colors.statLoss,
                )
            }
        }
        item { StatTilesGrid(stats = stats) }
        if (state.attendanceMonths.isNotEmpty()) {
            item { AttendanceCalendar(months = state.attendanceMonths, activeDays = state.attendanceDays) }
        }
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

/**
 * Centered profile hero: a ringed avatar (with a "+" edit badge on own profile), the name
 * below it with a rename pencil, and a meta row of group · win rate · matches.
 */
@Composable
private fun ProfileHero(
    stats: PlayerStats,
    displayName: String,
    photoUrl: String?,
    avatarId: String?,
    editable: Boolean,
    isUploadingPhoto: Boolean,
    onEditName: () -> Unit,
    onEditAvatar: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        HeroAvatar(
            displayName = displayName,
            photoUrl = photoUrl,
            avatarId = avatarId,
            avatarColorHex = stats.avatarColor,
            editable = editable,
            isUploading = isUploadingPhoto,
            onEdit = onEditAvatar,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Phantom spacer mirrors the pencil cluster (gap + badge) so the NAME itself
            // stays optically centered, with the pencil hanging off to its right.
            if (editable) Spacer(Modifier.width(32.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp, lineHeight = 30.sp),
                fontWeight = FontWeight.Bold,
                color = PlayboardTheme.colors.textPrimary,
                maxLines = 1,
            )
            if (editable) {
                Spacer(Modifier.width(4.dp))
                EditBadge(onClick = onEditName)
            }
        }
        Spacer(Modifier.height(12.dp))
        HeroMetaRow(
            winRatePercent = stats.winRatePercent,
            matchesPlayed = stats.matchesPlayed,
        )
    }
}

/**
 * The hero avatar: a large [PlayerAvatar] inside a thin light ring, with a "+" edit badge
 * on the bottom-right for own profile (a spinner overlay while an upload is in flight).
 * A viewed player renders the ringed avatar only.
 */
@Composable
private fun HeroAvatar(
    displayName: String,
    photoUrl: String?,
    avatarId: String?,
    avatarColorHex: String,
    editable: Boolean,
    isUploading: Boolean,
    onEdit: () -> Unit,
) {
    val size = 96.dp
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size + 10.dp)
                .clip(CircleShape)
                .border(2.dp, PlayboardTheme.colors.textPrimary.copy(alpha = 0.85f), CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            PlayerAvatar(
                displayName = displayName,
                photoUrl = photoUrl,
                avatarId = avatarId,
                avatarColorHex = avatarColorHex,
                size = size,
                modifier = if (editable && !isUploading) {
                    Modifier.clip(CircleShape).clickable(onClick = onEdit)
                } else {
                    Modifier
                },
            )
        }
        if (isUploading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(PlayboardTheme.colors.surface.copy(alpha = 0.6f)),
            ) {
                CircularProgressIndicator(color = PlayboardTheme.colors.brand, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
        } else if (editable) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(PlayboardTheme.colors.brand)
                    .border(2.dp, PlayboardTheme.colors.background, CircleShape)
                    .clickable(onClick = onEdit),
            ) {
                Text(
                    text = "+",
                    color = PlayboardTheme.colors.onBrand,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

/** Centered meta row: win rate · matches (group name already shows in the top switcher). */
@Composable
private fun HeroMetaRow(
    winRatePercent: Int,
    matchesPlayed: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = PlayboardTheme.colors.brand, fontWeight = FontWeight.SemiBold)) {
                    append("$winRatePercent%")
                }
                append(" win rate")
            },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
            color = PlayboardTheme.colors.textMuted,
            maxLines = 1,
        )
        MetaDot()
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.matches),
            contentDescription = null,
            tint = PlayboardTheme.colors.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        MetaText("$matchesPlayed ${if (matchesPlayed == 1) "match" else "matches"}")
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
        color = PlayboardTheme.colors.textMuted,
        maxLines = 1,
    )
}

@Composable
private fun MetaDot() {
    Text(
        text = "·",
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
        color = PlayboardTheme.colors.textMuted,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/** Subtle tappable pencil next to the editable display name (rename) — no chip/outline. */
@Composable
private fun EditBadge(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.pencil),
            contentDescription = "Edit name",
            tint = PlayboardTheme.colors.textMuted,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun StatTilesGrid(stats: PlayerStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // IntrinsicSize.Min + fillMaxHeight makes every tile in a row match the
        // tallest one, so a sub-label (e.g. "Best: N") can't leave them uneven.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            StatTile("WINS", stats.wins.toString(), modifier = Modifier.weight(1f).fillMaxHeight())
            StatTile("LOSSES", stats.losses.toString(), modifier = Modifier.weight(1f).fillMaxHeight())
            StatTile("PTS FOR", stats.pointsFor.toString(), modifier = Modifier.weight(1f).fillMaxHeight())
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            StatTile(
                "CURRENT STREAK",
                stats.currentStreak.toString(),
                valueColor = PlayboardTheme.colors.brand,
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
    valueColor: Color = PlayboardTheme.colors.textPrimary,
    subLabel: String? = null,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = PlayboardTheme.colors.surface, modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp, lineHeight = 28.sp),
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
            Spacer(Modifier.height(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted, textAlign = TextAlign.Center)
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = PlayboardTheme.colors.textMuted,
                    modifier = Modifier.padding(top = 2.dp),
                    textAlign = TextAlign.Center
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
        Surface(shape = RoundedCornerShape(16.dp), color = PlayboardTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
            ) {
                PlayerAvatar(
                    displayName = partner.displayName,
                    photoUrl = partner.photoUrl,
                    avatarId = partner.avatarId,
                    avatarColorHex = partner.avatarColor,
                    size = 44.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = partner.displayName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = PlayboardTheme.colors.textPrimary,
                    )
                    Text(
                        text = "${partner.winsTogether}W / ${partner.gamesTogether} games together",
                        style = MaterialTheme.typography.labelSmall,
                        color = PlayboardTheme.colors.textMuted,
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
    val accent = if (row.isWin) PlayboardTheme.colors.brand else PlayboardTheme.colors.statLoss
    Surface(shape = RoundedCornerShape(14.dp), color = PlayboardTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ResultBadge(isWin = row.isWin)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = dateLabel(row.playedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = PlayboardTheme.colors.textMuted,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        if (row.partnerNames.isNotBlank()) append("w/ ${row.partnerNames} ")
                        append("vs ${row.opponentNames}")
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = PlayboardTheme.colors.textPrimary,
                )
                if (row.sets.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = row.sets.joinToString(", ") { "${it.team1Score}-${it.team2Score}" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                        color = PlayboardTheme.colors.brand,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultBadge(isWin: Boolean) {
    val color = if (isWin) PlayboardTheme.colors.brand else PlayboardTheme.colors.statLoss
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (isWin) "WIN" else "LOSS",
            color = if (isWin) PlayboardTheme.colors.onBrand else PlayboardTheme.colors.textPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** "← Back" row shown atop a drilled-in player's profile; returns to the leaderboard. */
@Composable
private fun BackRow(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onBack)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Text(text = "←", color = PlayboardTheme.colors.brand, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Leaderboard",
            color = PlayboardTheme.colors.brand,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = PlayboardTheme.colors.textMuted,
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
            color = PlayboardTheme.colors.textMuted,
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
    avatarId = null,
    avatarColor = "#9ADE28",
    matchesPlayed = 8,
    wins = 4,
    losses = 4,
    pointsFor = 315,
    pointsAgainst = 320,
    winRate = 0.5,
    currentStreak = 2,
    bestStreak = 2,
    bestPartner = BestPartner("u2", "Dev", null, null, "#3DB4FF", gamesTogether = 2, winsTogether = 2, winRate = 1.0),
    recentMatches = listOf(
        Match(
            id = "m1",
            playedAt = Instant.parse("2026-07-09T06:58:00Z"),
            teams = listOf(
                MatchTeam(1, true, listOf(MatchPlayer("u1", "Raj", "#9ADE28", null, null), MatchPlayer("u2", "Dev", "#3DB4FF", null, null))),
                MatchTeam(2, false, listOf(MatchPlayer("u3", "Marcus", "#FF8A3D", null, null), MatchPlayer("u4", "Kiran", "#EAC72B", null, null))),
            ),
            sets = listOf(MatchSet(1, 21, 12), MatchSet(2, 21, 17)),
        ),
    ),
)

private val previewAttendanceMonths = heatmapMonths(LocalDate.of(2026, 7, 19))
private val previewAttendanceDays: Set<LocalDate> = setOf(
    LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 20),
    LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 20),
    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 27),
    LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 13),
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
                attendanceMonths = previewAttendanceMonths,
                attendanceDays = previewAttendanceDays,
            ),
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, heightDp = 1100)
@Composable
private fun ViewedPlayerProfilePreview() {
    PlayboardTheme {
        // Drill-down from the leaderboard: back row shown, no account section.
        ProfileContent(
            state = ProfileUiState(
                isLoading = false,
                groupName = "Saturday Smashers",
                isOwnProfile = false,
                stats = previewStats,
                attendanceMonths = previewAttendanceMonths,
                attendanceDays = previewAttendanceDays,
            ),
            onRetry = {},
            onBack = {},
        )
    }
}
