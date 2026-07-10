package com.org.playboard.ui.main

import androidx.annotation.DrawableRes
import com.org.playboard.R

/**
 * Bottom-bar destinations (docs/requirements/00-overview.md § Navigation).
 * [Add] renders as the floating center action instead of a regular tab item.
 */
enum class MainTab(val label: String, @DrawableRes val iconRes: Int?) {
    Board("BOARD", R.drawable.ic_tab_board),
    Matches("MATCHES", R.drawable.ic_tab_matches),
    Add("ADD", null),
    Profile("PROFILE", R.drawable.ic_tab_profile),
}
