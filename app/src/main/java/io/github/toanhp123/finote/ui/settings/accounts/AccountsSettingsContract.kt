package io.github.toanhp123.finote.ui.settings.accounts

import io.github.toanhp123.finote.domain.account.model.Account

/**
 * [accounts] are the live ones: followed first by `sortOrder`, ignored last. [selected] is the account whose sheet is
 * open and [mergeTargets] are the live accounts of the same bank it could merge into. [pendingMerge] is source to
 * target while the merge dialog is open, with [mergeCount] transactions that would move. [trashableCount] is what
 * the ignore dialog would offer to trash.
 */
data class AccountsSettingsState(
    val accounts: List<Account> = emptyList(),
    val selected: Account? = null,
    val mergeTargets: List<Account> = emptyList(),
    val pendingMerge: Pair<Account, Account>? = null,
    val mergeCount: Int = 0,
    val isIgnoreDialogOpen: Boolean = false,
    val trashableCount: Int = 0,
)

sealed interface AccountsSettingsIntent {
    data class AccountClicked(
        val id: String,
    ) : AccountsSettingsIntent

    data object SheetDismissed : AccountsSettingsIntent

    data class MergeTargetPicked(
        val targetId: String,
    ) : AccountsSettingsIntent

    data object MergeConfirmed : AccountsSettingsIntent

    data object IgnoreClicked : AccountsSettingsIntent

    data class IgnoreConfirmed(
        val shouldTrash: Boolean,
    ) : AccountsSettingsIntent

    data object FollowAgainClicked : AccountsSettingsIntent

    data object DialogDismissed : AccountsSettingsIntent
}

/** Each effect is the outcome of an action; the route shows it as a snackbar. */
sealed interface AccountsSettingsEffect {
    data object Merged : AccountsSettingsEffect

    data object Ignored : AccountsSettingsEffect

    data object FollowingAgain : AccountsSettingsEffect

    data object MergeBlockedByTransfer : AccountsSettingsEffect

    data object ActionFailed : AccountsSettingsEffect
}
