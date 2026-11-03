package io.github.toanhp123.finote.ui.common.component

import androidx.annotation.DrawableRes

/** One tab of [FinoteNavBar]. [label] is spoken by a screen reader. */
data class FinoteNavItem(
    @DrawableRes val iconRes: Int,
    val label: String,
)
