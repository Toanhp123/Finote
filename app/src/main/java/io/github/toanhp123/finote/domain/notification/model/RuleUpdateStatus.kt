package io.github.toanhp123.finote.domain.notification.model

/** The rule-update settings as the screen shows them. [lastCheck] is null until the first run. */
data class RuleUpdateStatus(
    val isAutoUpdateEnabled: Boolean,
    val lastCheck: RuleUpdateCheck?,
)
