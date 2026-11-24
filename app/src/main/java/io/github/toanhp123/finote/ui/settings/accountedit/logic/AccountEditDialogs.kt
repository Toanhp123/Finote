package io.github.toanhp123.finote.ui.settings.accountedit.logic

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier

/** Live accounts [source] could merge into: the same bank app and type, not archived. */
fun mergeCandidates(
    all: List<Account>,
    source: Account,
): List<Account> =
    all.filter {
        it.id != source.id &&
            it.deletedAt == null &&
            !it.isArchived &&
            it.bankPackageName == source.bankPackageName &&
            it.type == source.type
    }

/** True when removing a masked number would leave a bank account with none, so a new account may appear later. */
fun isLastIdentifier(
    account: Account,
    identifiers: List<AccountIdentifier>,
): Boolean = account.bankPackageName != null && identifiers.size <= 1
