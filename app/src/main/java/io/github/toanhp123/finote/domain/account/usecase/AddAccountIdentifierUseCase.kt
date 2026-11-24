package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.error.MergeRejection
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.repository.AccountMergeRepository
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.service.AccountRules
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import javax.inject.Inject

class AddAccountIdentifierUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val mergeRepository: AccountMergeRepository,
        private val tx: TransactionRunner,
    ) {
        /**
         * Links a masked number to the account. Checks run in this order: `NotFound`, `Archived`, `NoBankApp`,
         * `InvalidMask`, `IdentifierTaken` (a live other account owns the key). A key the account owns is a no-op; a
         * key of a deleted account moves over.
         */
        suspend operator fun invoke(
            accountId: String,
            kind: IdentifierKind,
            mask: String,
        ): Outcome<Unit, AccountError> =
            tx.write {
                val account = repository.getAccount(accountId)
                val packageName = account?.bankPackageName
                if (account == null || account.deletedAt != null) {
                    Outcome.Err(AccountError.NotFound)
                } else if (account.isArchived) {
                    Outcome.Err(AccountError.Archived)
                } else if (packageName == null || account.type == AccountType.CASH) {
                    Outcome.Err(AccountError.NoBankApp)
                } else if (!MASK.matches(mask)) {
                    Outcome.Err(AccountError.InvalidMask)
                } else {
                    val owner = repository.getIdentifier(packageName, kind, mask)
                    val holder = owner?.let { repository.getAccount(it.accountId) }
                    if (owner?.accountId == accountId) {
                        Outcome.Ok(Unit)
                    } else if (holder != null && holder.deletedAt == null) {
                        Outcome.Err(AccountError.IdentifierTaken(holder, mergeRejection(account, holder)))
                    } else {
                        repository.addIdentifier(AccountIdentifier(packageName, kind, mask, accountId))
                        Outcome.Ok(Unit)
                    }
                }
            }

        /** An archived holder blocks the merge here; else `AccountRules.checkMerge`, edited account as source. */
        private suspend fun mergeRejection(
            account: Account,
            holder: Account,
        ): MergeRejection? =
            if (holder.isArchived) {
                MergeRejection.ACCOUNT_ARCHIVED
            } else {
                AccountRules.checkMerge(
                    account.id,
                    holder.id,
                    account,
                    holder,
                    mergeRepository.countTransfersBetween(account.id, holder.id),
                )
            }

        private companion object {
            val MASK = Regex("^[0-9]{4}$")
        }
    }
