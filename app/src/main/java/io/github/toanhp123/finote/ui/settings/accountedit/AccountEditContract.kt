package io.github.toanhp123.finote.ui.settings.accountedit

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.BankApp
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.ui.settings.accountedit.logic.AccountDraft

/**
 * [draft] is the form: filled once from the first stored value (or empty in create mode, where [stored] is null and
 * stays null), then owned by the screen. [stored], [identifiers] and [bankApps] follow the database. [stored] turns
 * null when the account is gone. [dialog] is the open confirmation or input, if any. [loadedStart] is the stored start
 * the draft was filled from: Save sends a start only when the draft differs from it.
 */
data class AccountEditState(
    val draft: AccountDraft? = null,
    val stored: Account? = null,
    val identifiers: List<AccountIdentifier> = emptyList(),
    val bankApps: List<BankApp> = emptyList(),
    val dialog: AccountEditDialog? = null,
    val isSaving: Boolean = false,
    val loadedStart: Long? = null,
) {
    // The loaded start stays out of logs and crash reports.
    override fun toString() = "AccountEditState(isSaving=$isSaving)"
}

sealed interface AccountEditDialog {
    /**
     * [count] is how many transactions would move from [source] to [target]. [droppedStart] is the start balance of
     * [source] the merge would drop, or null when none is lost.
     */
    data class Merge(
        val source: Account,
        val target: Account,
        val count: Int,
        val droppedStart: Long? = null,
    ) : AccountEditDialog {
        // The dropped start is a balance: it stays out of logs and crash reports.
        override fun toString() = "Merge(sourceId=${source.id}, targetId=${target.id}, count=$count)"
    }

    /** The live accounts the edited one could merge into. */
    data class MergeTargetPicker(
        val targets: List<Account>,
    ) : AccountEditDialog

    /** [trashableCount] is what the dialog offers to trash; [shouldTrash] is the ticked option. */
    data class Ignore(
        val trashableCount: Int,
        val shouldTrash: Boolean = false,
    ) : AccountEditDialog

    data object Delete : AccountEditDialog

    /** The input for one masked number. It stays open while the entry is refused. */
    data object AddIdentifier : AccountEditDialog

    /** Asks before [identifier], the only masked number of a bank account, goes. */
    data class RemoveLastIdentifier(
        val identifier: AccountIdentifier,
    ) : AccountEditDialog

    /** The input for the real current balance. */
    data object Reconcile : AccountEditDialog
}

/** Why the screen leaves on its own. */
enum class CloseReason { DELETED, MERGED, GONE }

sealed interface AccountEditIntent {
    /** The whole form after one field changed. The view model keeps the locked fields (see `lockedBy`). */
    data class DraftChanged(
        val draft: AccountDraft,
    ) : AccountEditIntent

    data object SaveClicked : AccountEditIntent

    data object StarToggled : AccountEditIntent

    data object ReconcileClicked : AccountEditIntent

    data class ReconcileSubmitted(
        val actual: Long,
    ) : AccountEditIntent {
        // The typed balance stays out of logs and crash reports.
        override fun toString() = "ReconcileSubmitted"
    }

    data object AddIdentifierClicked : AccountEditIntent

    data class AddIdentifierSubmitted(
        val kind: IdentifierKind,
        val mask: String,
    ) : AccountEditIntent {
        // The mask stays out of logs and crash reports.
        override fun toString() = "AddIdentifierSubmitted(kind=$kind)"
    }

    data class RemoveIdentifierClicked(
        val identifier: AccountIdentifier,
    ) : AccountEditIntent

    data object RemoveIdentifierConfirmed : AccountEditIntent

    data object IgnoreClicked : AccountEditIntent

    data class TrashToggled(
        val isChecked: Boolean,
    ) : AccountEditIntent

    data object IgnoreConfirmed : AccountEditIntent

    data object FollowAgainClicked : AccountEditIntent

    data object ArchiveToggled : AccountEditIntent

    data object DeleteClicked : AccountEditIntent

    data object DeleteConfirmed : AccountEditIntent

    data object MergeClicked : AccountEditIntent

    data class MergeTargetPicked(
        val targetId: String,
    ) : AccountEditIntent

    data object MergeConfirmed : AccountEditIntent

    data object DialogDismissed : AccountEditIntent
}

sealed interface AccountEditEffect {
    /**
     * Leave the screen. A [reason] means the account left (deleted, merged or removed elsewhere) and the screen says
     * why; null means the form was written.
     */
    data class Close(
        val reason: CloseReason?,
    ) : AccountEditEffect

    /** A rule refused the change. The screen shows one message per [error], and per merge rejection inside it. */
    data class ShowError(
        val error: AccountError,
    ) : AccountEditEffect

    /** The balance correction was refused: the account is unusable, or the math overflowed. */
    data class ShowReconcileFailed(
        val error: TransactionError,
    ) : AccountEditEffect

    /** Merge was asked for, but no live account of the same bank and type is left to merge into. */
    data object ShowNoMergeTarget : AccountEditEffect

    /** A database failure: nothing was written. */
    data object ShowStorageFailed : AccountEditEffect
}
