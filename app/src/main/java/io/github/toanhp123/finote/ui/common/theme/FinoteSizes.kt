package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class FinoteSizes(
    val iconSm: Dp = 20.dp,
    val iconMd: Dp = 24.dp,
    val avatar: Dp = 36.dp,
    val touchTarget: Dp = 48.dp,
    /** Narrowest title a list row keeps next to its trailing content before the trailing content moves below. */
    val titleMinWidth: Dp = 96.dp,
    val border: Dp = 1.dp,
    val navBarHeight: Dp = 56.dp,
    val navItemWidth: Dp = 56.dp,
    val navItemHeight: Dp = 40.dp,
    /** Blur radius of a glass surface over the content behind it. */
    val glassBlur: Dp = 20.dp,
    val emptyIcon: Dp = 72.dp,
    val progressStroke: Dp = 2.dp,
    val sheetHandleWidth: Dp = 32.dp,
    val sheetHandleHeight: Dp = 4.dp,
    val dialogMaxWidth: Dp = 360.dp,
    val switchTrackWidth: Dp = 52.dp,
    val switchTrackHeight: Dp = 32.dp,
    val switchThumb: Dp = 24.dp,
)
