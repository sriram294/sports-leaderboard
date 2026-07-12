package com.org.playboard.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.ui.add.AddMatchScreen
import com.org.playboard.ui.board.BoardScreen
import com.org.playboard.ui.matches.MatchesScreen
import com.org.playboard.ui.profile.ProfileScreen
import com.org.playboard.ui.switcher.GroupSwitcher
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.OnBrandLime
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted

/**
 * Post-login shell: the 5-tab bottom bar present on every screen
 * (docs/requirements/00-overview.md § Navigation). All tabs are real except
 * Stats, which shows an "Insights coming soon" placeholder until its slice lands.
 */
@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Board) }
    // Set when the user taps "Edit" on a match → the Add tab opens pre-filled in
    // edit mode; null means a fresh "record a match" form.
    var pendingEditMatchId by rememberSaveable { mutableStateOf<String?>(null) }
    // Set when a leaderboard row is tapped → the Board tab drills into that player's
    // profile in place; null shows the leaderboard (docs/requirements/02 §2).
    var viewingProfileUserId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            MainBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    // Tapping the + tab always starts a fresh (create) form.
                    if (tab == MainTab.Add) pendingEditMatchId = null
                    // Any tab tap leaves a leaderboard drill-down (so Board returns home).
                    viewingProfileUserId = null
                    selectedTab = tab
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Shared group switcher — the top header on every tab, in place of the
            // old per-page titles (docs/requirements/00-overview.md § Group). The
            // Scaffold's inner padding already clears the status bar, so no extra
            // statusBarsPadding() here (that was double-counting the inset).
            GroupSwitcher(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
                    MainTab.Stats -> PlaceholderTab("Insights coming soon")
                    MainTab.Profile -> ProfileScreen()
                }
            }
        }
    }
}

@Composable
private fun MainBottomBar(selectedTab: MainTab, onTabSelected: (MainTab) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
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
    val tint = if (isSelected) BrandLime else TextMuted
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
            Box(modifier = Modifier.width(28.dp).height(2.dp).background(BrandLime))
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
                .background(BrandLime),
        ) {
            Text(
                text = "+",
                color = OnBrandLime,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp, lineHeight = 32.sp),
            )
        }
        Text(
            text = MainTab.Add.label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.offset(y = (-6).dp),
        )
    }
}

@Composable
private fun PlaceholderTab(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}
