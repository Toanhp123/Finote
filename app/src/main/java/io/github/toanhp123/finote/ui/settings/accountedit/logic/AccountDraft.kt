package io.github.toanhp123.finote.ui.settings.accountedit.logic

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.ui.common.theme.color.CategoryPaletteArgb

/**
 * The fields the edit screen owns until Save. [startBalance] is VND; null is an empty field (create mode only: an edit
 * draft always starts from the stored start).
 */
data class AccountDraft(
    val name: String,
    val type: AccountType,
    val icon: String,
    val color: Int,
    val isExcludedFromNetWorth: Boolean,
    val bankPackageName: String?,
    val startBalance: Long?,
) {
    // The start balance stays out of logs and crash reports.
    override fun toString() = "AccountDraft(type=$type)"
}

/** What the create screen starts with. The color is the first one the picker offers, so one is always picked. */
val NewAccountDraft =
    AccountDraft(
        name = "",
        type = AccountType.BANK,
        icon = "payments",
        color = CategoryPaletteArgb.first(),
        isExcludedFromNetWorth = false,
        bankPackageName = null,
        startBalance = null,
    )

fun Account.toDraft() =
    AccountDraft(
        name = name,
        type = type,
        icon = icon,
        color = color,
        isExcludedFromNetWorth = isExcludedFromNetWorth,
        bankPackageName = bankPackageName,
        startBalance = initialBalance,
    )

fun AccountDraft.isDirtyAgainst(stored: Account): Boolean = this != stored.toDraft()

/**
 * The account to save. With a [base] only the display fields and a first bank app change: type, an existing bank app,
 * balances and flags stay. Without one it is a new account that the create use case stamps.
 */
fun AccountDraft.toAccount(
    base: Account?,
    id: String,
): Account {
    val trimmed = name.trim()
    return base?.copy(
        name = trimmed,
        icon = icon,
        color = color,
        isExcludedFromNetWorth = isExcludedFromNetWorth,
        bankPackageName = base.bankPackageName ?: bankPackageName,
    ) ?: Account(
        id = id,
        name = trimmed,
        type = type,
        initialBalance = startBalance ?: 0L,
        currentBalance = startBalance ?: 0L,
        currency = "VND",
        icon = icon,
        color = color,
        sortOrder = 0,
        isExcludedFromNetWorth = isExcludedFromNetWorth,
        isArchived = false,
        isInitialized = false,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        bankPackageName = bankPackageName,
    )
}

/**
 * The start to send to Save: only a provisional start that the user changed since the draft was filled ([loadedStart]);
 * null leaves the stored start alone. The stored start is no yardstick: a booking re-derives it while provisional.
 */
fun AccountDraft.startToSave(
    stored: Account,
    loadedStart: Long?,
): Long? = startBalance?.takeIf { !stored.isInitialized && it != loadedStart }

/**
 * Applies the locks to a field change: [previous] is the draft before it. An existing account keeps its type, its bank
 * app once set, and a fixed start. A cash account never has a bank app.
 */
fun AccountDraft.lockedBy(
    stored: Account?,
    previous: AccountDraft,
): AccountDraft =
    if (stored == null) {
        copy(bankPackageName = bankPackageName.takeIf { type != AccountType.CASH })
    } else {
        copy(
            type = previous.type,
            bankPackageName = stored.bankPackageName ?: bankPackageName.takeIf { previous.type != AccountType.CASH },
            startBalance = if (stored.isInitialized) previous.startBalance else startBalance,
        )
    }
