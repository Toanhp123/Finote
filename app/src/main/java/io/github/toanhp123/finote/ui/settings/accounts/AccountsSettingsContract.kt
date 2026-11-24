package io.github.toanhp123.finote.ui.settings.accounts

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.settings.accounts.logic.AccountListItem

/**
 * [items] are the live accounts as rows and [archived] the archived ones. [suggestions] maps an account id to the id
 * of the account it may duplicate. [expanded] holds the packages of the open groups. [reorder] is the draft order of
 * the top-level rows while the reorder mode is on, and null otherwise.
 */
data class AccountsSettingsState(
    val items: List<AccountListItem> = emptyList(),
    val archived: List<Account> = emptyList(),
    val suggestions: Map<String, String> = emptyMap(),
    val expanded: Set<String> = emptySet(),
    val isArchivedExpanded: Boolean = false,
    val reorder: List<AccountListItem>? = null,
)

sealed interface AccountsSettingsIntent {
    data class AccountClicked(
        val id: String,
    ) : AccountsSettingsIntent

    data object AddClicked : AccountsSettingsIntent

    data class GroupToggled(
        val packageName: String,
    ) : AccountsSettingsIntent

    data object ArchivedToggled : AccountsSettingsIntent

    /** The user opened the merge of [id] with its suggested partner. */
    data class SuggestionClicked(
        val id: String,
    ) : AccountsSettingsIntent

    /** The user said [id] and its suggested partner are not the same account. */
    data class SuggestionDismissed(
        val id: String,
    ) : AccountsSettingsIntent

    data object ReorderClicked : AccountsSettingsIntent

    /** The draft row at index [from] was dropped at index [to]. */
    data class ReorderMoved(
        val from: Int,
        val to: Int,
    ) : AccountsSettingsIntent

    data object ReorderDone : AccountsSettingsIntent

    data object ReorderCancelled : AccountsSettingsIntent
}

sealed interface AccountsSettingsEffect {
    /** Open the edit screen of [id], or the create screen when [id] is null. [mergeTargetId] opens its merge dialog. */
    data class OpenAccount(
        val id: String?,
        val mergeTargetId: String? = null,
    ) : AccountsSettingsEffect

    /** A write failed; the route shows a snackbar. */
    data object ActionFailed : AccountsSettingsEffect
}
