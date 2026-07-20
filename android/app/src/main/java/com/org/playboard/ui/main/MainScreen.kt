package com.org.playboard.ui.main
import com.org.playboard.ui.theme.PlayboardTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.R
import com.org.playboard.ui.components.AppWordmark
import com.org.playboard.ui.components.playboardGlow
import com.org.playboard.ui.add.AddMatchScreen
import com.org.playboard.ui.board.BoardScreen
import com.org.playboard.ui.group.GroupManagementScreen
import com.org.playboard.ui.matches.MatchesScreen
import com.org.playboard.ui.profile.ProfileScreen
import com.org.playboard.ui.profile.SettingsScreen
import com.org.playboard.ui.stats.StatsScreen
import com.org.playboard.ui.switcher.GroupSwitcher
import com.org.playboard.ui.update.AppUpdateViewModel

/**
 * Side gutter for the shared header. This is the app-wide page gutter every screen
 * uses (see PROJECT_RULES.md), so the wordmark, the group switcher and each tab's
 * content all line up on the same vertical edges.
 */
private val HeaderGutter = 10.dp

/**
 * Post-login shell: the 5-tab bottom bar present on every screen
 * (docs/requirements/00-overview.md § Navigation). Every tab is implemented;
 * Stats provides the group Insights dashboard.
 */
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel(), updateViewModel: AppUpdateViewModel? = null) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Board) }
    // Set when the user taps "Edit" on a match → the Add tab opens pre-filled in
    // edit mode; null means a fresh "record a match" form.
    var pendingEditMatchId by rememberSaveable { mutableStateOf<String?>(null) }
    // Set when a leaderboard row is tapped → the Board tab drills into that player's
    // profile in place; null shows the leaderboard (docs/requirements/02 §2).
    var viewingProfileUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingProfileSettings by rememberSaveable { mutableStateOf(false) }
    // Settings renders inside the Profile tab, so the header gear has to switch tabs to
    // open it. This records the tab the user was actually on so closing Settings returns
    // there rather than stranding them on a Profile tab they never asked for. Null when
    // Settings was opened the original way (from within Profile).
    var settingsReturnTab by rememberSaveable { mutableStateOf<MainTab?>(null) }
    // Set when the profile's group icon is tapped → the group-management drill-down opens
    // over the Profile tab (its own internal Back handling unwinds it).
    var showingGroupManagement by rememberSaveable { mutableStateOf(false) }
    // The trail of tabs the user navigated away from, so system Back steps back through
    // them (Stats -> Profile -> Board -> exit) instead of closing the app. Saved across
    // rotation/process death; enums stored by name.
    val tabHistory = rememberSaveable(
        saver = listSaver(
            save = { it.map(MainTab::name) },
            restore = { it.map(MainTab::valueOf).toMutableStateList() },
        ),
    ) { mutableStateListOf<MainTab>() }

    // Opening Settings from the header gear: it lives under the Profile tab, so jump
    // there, clearing any in-place drill-down the way a tab tap does.
    val openSettings = {
        if (!showingProfileSettings) {
            settingsReturnTab = selectedTab.takeIf { it != MainTab.Profile }
            viewingProfileUserId = null
            showingGroupManagement = false
            selectedTab = MainTab.Profile
            showingProfileSettings = true
        }
    }
    val closeSettings = {
        showingProfileSettings = false
        settingsReturnTab?.let { selectedTab = it }
        settingsReturnTab = null
    }

    // System Back / back-swipe: unwind in-app navigation (which lives in the state above,
    // not the NavController back stack). Priority: sub-screens first, then the edit form,
    // then the tab trail, then home. Disabled on a clean Board so Back exits the app.
    val canGoBack = showingProfileSettings ||
        viewingProfileUserId != null ||
        (selectedTab == MainTab.Add && pendingEditMatchId != null) ||
        tabHistory.isNotEmpty() ||
        selectedTab != MainTab.Board
    BackHandler(enabled = canGoBack) {
        when {
            showingProfileSettings -> closeSettings()
            viewingProfileUserId != null -> viewingProfileUserId = null
            selectedTab == MainTab.Add && pendingEditMatchId != null -> {
                // An edit form was opened from the Matches log — Back returns there.
                pendingEditMatchId = null
                selectedTab = MainTab.Matches
            }
            tabHistory.isNotEmpty() -> selectedTab = tabHistory.removeAt(tabHistory.lastIndex)
            selectedTab != MainTab.Board -> selectedTab = MainTab.Board // safety net: never exit off a non-home tab
        }
    }

    // Switching the active group must pop any open leaderboard drill-down — the
    // viewed player belongs to the previous group. Only clears on a real id change
    // (not the initial resolve or a same-group silent refresh), so rotation and
    // process-death restore keep the drill-down.
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    var lastGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedGroupId) {
        val current = selectedGroupId ?: return@LaunchedEffect
        if (lastGroupId != null && lastGroupId != current) {
            viewingProfileUserId = null
        }
        lastGroupId = current
    }

    // The ambient glow is painted once, here, behind the Scaffold — not per tab, since
    // each tab's content starts below the header and would anchor the glow's origin wrong.
    // The Scaffold itself must stay transparent for it to show through; that in turn means
    // spelling out contentColor, because Material derives it from containerColor and
    // resolves Transparent to an unspecified (black) content color.
    Scaffold(
        modifier = Modifier.playboardGlow(PlayboardTheme.colors),
        containerColor = Color.Transparent,
        contentColor = PlayboardTheme.colors.textPrimary,
        bottomBar = {
            MainBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    // Tapping the + tab always starts a fresh (create) form.
                    if (tab == MainTab.Add) pendingEditMatchId = null
                    // Any tab tap leaves a leaderboard drill-down (so Board returns home).
                    viewingProfileUserId = null
                    showingProfileSettings = false
                    settingsReturnTab = null
                    showingGroupManagement = false
                    // Record the tab we're leaving so Back can step back through the trail.
                    if (tab != selectedTab) {
                        tabHistory.add(selectedTab)
                        selectedTab = tab
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // App header: the wordmark identifies the app on every tab, with the
            // group switcher demoted to a slim pill beneath it. Both sit at the
            // same 10.dp gutter the Board content uses, so the switcher's edges
            // line up with the leaderboard card below it.
            // The Scaffold's inner padding already clears the status bar, so no extra
            // statusBarsPadding() here (that was double-counting the inset).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = HeaderGutter, end = HeaderGutter, top = 8.dp),
            ) {
                AppWordmark(
                    logoHeight = 26.dp,
                    fontSize = 20.sp,
                    horizontalArrangement = Arrangement.Start,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = openSettings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = "Settings",
                        tint = PlayboardTheme.colors.textMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            GroupSwitcher(
                modifier = Modifier.padding(horizontal = HeaderGutter, vertical = 8.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    MainTab.Board ->
                        if (viewingProfileUserId != null) {
                            // Drill-down: show the tapped player's profile in place, keeping
                            // the Board tab selected; Back returns to the leaderboard.
                            ProfileScreen(
                                viewedUserId = viewingProfileUserId,
                                onBack = { viewingProfileUserId = null },
                            )
                        } else {
                            BoardScreen(onPlayerClick = { viewingProfileUserId = it })
                        }
                    MainTab.Matches -> MatchesScreen(
                        onEditMatch = { matchId ->
                            pendingEditMatchId = matchId
                            selectedTab = MainTab.Add
                        },
                    )
                    MainTab.Add -> AddMatchScreen(
                        editMatchId = pendingEditMatchId,
                        onRecorded = {
                            // Edits return to the Matches log; new matches go to the Board.
                            val wasEdit = pendingEditMatchId != null
                            pendingEditMatchId = null
                            selectedTab = if (wasEdit) MainTab.Matches else MainTab.Board
                        },
                    )
                    MainTab.Stats -> StatsScreen()
                    MainTab.Profile -> when {
                        showingProfileSettings -> SettingsScreen(
                            updateViewModel = updateViewModel,
                            onBack = closeSettings,
                        )
                        showingGroupManagement -> GroupManagementScreen(
                            onExit = { showingGroupManagement = false },
                        )
                        else -> ProfileScreen(
                            onOpenSettings = openSettings,
                            onOpenGroupManagement = { showingGroupManagement = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainBottomBar(selectedTab: MainTab, onTabSelected: (MainTab) -> Unit) {
    // Transparent so the ambient glow runs unbroken to the bottom of the screen instead of
    // being cut off by a 76.dp slab. Safe because the Scaffold's inner padding already
    // reserves this bar's height, so content never scrolls underneath it — the only thing
    // behind the bar is the background itself. A hairline keeps the bar legible as a bar.
    val hairline = PlayboardTheme.colors.textMuted.copy(alpha = 0.12f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(hairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .navigationBarsPadding()
            .height(76.dp),
    ) {
        MainTab.entries.forEach { tab ->
            if (tab == MainTab.Add) {
                AddTabItem(onClick = { onTabSelected(MainTab.Add) }, modifier = Modifier.weight(1f))
            } else {
                TabItem(
                    tab = tab,
                    isSelected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(tab: MainTab, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = if (isSelected) PlayboardTheme.colors.brand else PlayboardTheme.colors.textMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(top = 14.dp),
    ) {
        tab.iconRes?.let { iconRes ->
            Icon(
                painter = painterResource(iconRes),
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(3.dp))
            Box(modifier = Modifier.width(28.dp).height(2.dp).background(PlayboardTheme.colors.brand))
        }
    }
}

/**
 * The prototype's floating lime "+" — a circular action rather than a regular tab
 * item. Larger than the other tabs and lifted above the bar so it reads as the
 * primary action, centered across the 5 items.
 */
@Composable
private fun AddTabItem(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(y = (-10).dp)
                .size(58.dp)
                .clip(CircleShape)
                .background(PlayboardTheme.colors.brand),
        ) {
            Text(
                text = "+",
                color = PlayboardTheme.colors.onBrand,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp, lineHeight = 32.sp),
            )
        }
        Text(
            text = MainTab.Add.label,
            style = MaterialTheme.typography.labelSmall,
            color = PlayboardTheme.colors.textMuted,
            modifier = Modifier.offset(y = (-6).dp),
        )
    }
}
