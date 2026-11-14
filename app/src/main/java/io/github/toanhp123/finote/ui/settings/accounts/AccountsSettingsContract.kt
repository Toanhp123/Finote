package io.github.toanhp123.finote.ui.settings.accounts

import io.github.toanhp123.finote.domain.account.model.Account

/**
 * [accounts] are the live ones: followed first by `sortOrder`, ignored last. [selected] is the account whose sheet is
 * open and [mergeTargets] are the live accounts of the same bank it could merge into. [dialog] is the open
 * confirmation, if any.
 */
data class AccountsSettingsState(
    val accounts: List<Account> = emptyList(),
    val selected: Account? = null,
    val mergeTargets: List<Account> = emptyList(),
    val dialog: AccountsDialog? = null,
)

sealed interface AccountsDialog {
    /** [count] is how many transactions would move from [source] to [target]. */
    data class Merge(
        val source: Account,
        val target: Account,
        val count: Int,
    ) : AccountsDialog

    /** [trashableCount] is what the dialog offers to trash; [shouldTrash] is the ticked option. */
    data class Ignore(
        val trashableCount: Int,
        val shouldTrash: Boolean = false,
    ) : AccountsDialog
}

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

    data class TrashToggled(
        val isChecked: Boolean,
    ) : AccountsSettingsIntent

    data object IgnoreConfirmed : AccountsSettingsIntent

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
