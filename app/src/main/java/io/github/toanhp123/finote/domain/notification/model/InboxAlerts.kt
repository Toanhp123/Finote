package io.github.toanhp123.finote.domain.notification.model

/** What the app tells the user about the failed inbox: [shownCount] items, a red dot, a banner above the list. */
data class InboxAlerts(
    val shownCount: Int,
    val hasUnseen: Boolean,
    val isBannerVisible: Boolean,
)
