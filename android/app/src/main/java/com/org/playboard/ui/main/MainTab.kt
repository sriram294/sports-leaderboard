package com.org.playboard.ui.main

import androidx.annotation.DrawableRes
import com.org.playboard.R

/**
 * Bottom-bar destinations (docs/requirements/00-overview.md § Navigation).
 * [Add] renders as the floating center action instead of a regular tab item.
 */
enum class MainTab(val label: String, @DrawableRes val iconRes: Int?) {
    Board("BOARD", R.drawable.boards),
    Matches("MATCHES", R.drawable.matches),
    // Add sits in the middle so the floating "+" is centered across the 5 items.
    Add("ADD", null),
    Stats("STATS", R.drawable.stats),
    Profile("PROFILE", R.drawable.profile),
}
