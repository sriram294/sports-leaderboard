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
import com.org.playboard.ui.board.BoardScreen
import com.org.playboard.ui.profile.ProfilePlaceholderScreen
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.OnBrandLime
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted

/**
 * Post-login shell: the 4-tab bottom bar present on every screen
 * (docs/requirements/00-overview.md § Navigation). Board is real; the other
 * tabs are placeholders until their slices land.
 */
@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Board) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { MainBottomBar(selectedTab = selectedTab, onTabSelected = { selectedTab = it }) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                MainTab.Board -> BoardScreen(onProfileClick = { selectedTab = MainTab.Profile })
                MainTab.Matches -> PlaceholderTab("Matches — coming in the next slice.")
                MainTab.Add -> PlaceholderTab("Add match — coming in the next slice.")
                MainTab.Profile -> ProfilePlaceholderScreen()
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

/** The prototype's floating lime "+" — a circular action rather than a regular tab item. */
@Composable
private fun AddTabItem(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(top = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(BrandLime),
        ) {
            Text(
                text = "+",
                color = OnBrandLime,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp, lineHeight = 24.sp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = MainTab.Add.label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
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
