package com.org.playboard.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            .padding(horizontal = 20.dp),
    ) {
        // Drill-down back affordance (leaderboard → player); absent on the Profile tab.
        if (onBack != null) {
            BackRow(onBack = onBack)
        }
        if (state.isOwnProfile && onBack == null && onOpenSettings != null) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = "Settings",
                        tint = PlayboardTheme.colors.textPrimary,
                    )
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
            IdentityCard(
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
private fun IdentityCard(
    stats: PlayerStats,
    displayName: String,
    photoUrl: String?,
    avatarId: String?,
    editable: Boolean,
    isUploadingPhoto: Boolean,
    onEditName: () -> Unit,
    onEditAvatar: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PlayboardTheme.colors.surface,
        border = BorderStroke(1.5.dp, PlayboardTheme.colors.brand),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp),
        ) {
            EditableAvatar(
                displayName = displayName,
                photoUrl = photoUrl,
                avatarId = avatarId,
                avatarColorHex = stats.avatarColor,
                editable = editable,
                isUploading = isUploadingPhoto,
                onEdit = onEditAvatar,
            )
            Spacer(Modifier.width(18.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp, lineHeight = 28.sp),
                        color = PlayboardTheme.colors.brand,
                    )
                    if (editable) {
                        Spacer(Modifier.width(8.dp))
                        EditBadge(onClick = onEditName)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${stats.matchesPlayed} ${if (stats.matchesPlayed == 1) "match" else "matches"} played",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = PlayboardTheme.colors.textMuted,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${stats.winRatePercent}%",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, lineHeight = 28.sp),
                        fontWeight = FontWeight.Bold,
                        color = PlayboardTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "WIN RATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = PlayboardTheme.colors.textMuted,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * The identity avatar with an edit affordance for own profile (req #3): a lime
 * pencil badge over the corner, tappable to pick a new photo; a spinner overlay
 * while the upload is in flight. Non-editable (a viewed player) renders a plain
 * [PlayerAvatar].
 */
@Composable
private fun EditableAvatar(
    displayName: String,
    photoUrl: String?,
    avatarId: String?,
    avatarColorHex: String,
    editable: Boolean,
    isUploading: Boolean,
    onEdit: () -> Unit,
) {
    val size = 76.dp
    Box(contentAlignment = Alignment.Center) {
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
        if (isUploading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(PlayboardTheme.colors.surface.copy(alpha = 0.6f)),
            ) {
                CircularProgressIndicator(color = PlayboardTheme.colors.brand, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        } else if (editable) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(PlayboardTheme.colors.brand)
                    .border(2.dp, PlayboardTheme.colors.surface, CircleShape)
                    .clickable(onClick = onEdit),
            ) {
    //          Text("✎", color = PlayboardTheme.colors.onBrand, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.pencil),
                    contentDescription = "Edit",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    tint = Color.Black // Retains original asset colors
                )
            }
        }
    }
}

/** Small tappable pencil chip next to the editable display name. */
@Composable
private fun EditBadge(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(PlayboardTheme.colors.surface)
            .border(1.dp, PlayboardTheme.colors.brand.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick),
    ) {
//        Text("✎", color = PlayboardTheme.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)

        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.pencil),
            contentDescription = "Edit",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
            tint = PlayboardTheme.colors.brand // Retains original asset colors
        )
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
            ),
            onRetry = {},
            onBack = {},
        )
    }
}
