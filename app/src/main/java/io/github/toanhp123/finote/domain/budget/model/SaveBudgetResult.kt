package io.github.toanhp123.finote.domain.budget.model

/** [shouldAskNotificationPermission] is true once: for the first successful save before the app has asked. */
data class SaveBudgetResult(
    val shouldAskNotificationPermission: Boolean,
)
