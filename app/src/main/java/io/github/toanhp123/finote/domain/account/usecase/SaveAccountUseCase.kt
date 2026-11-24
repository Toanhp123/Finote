package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.service.AccountRules
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class SaveAccountUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Saves the display fields, links the bank app when the account has none yet, and, while the stored start is
         * provisional, fixes it to [startBalance] when that differs. A rename alone keeps a provisional account
         * provisional. Only the name is checked: currency is not editable.
         */
        suspend operator fun invoke(
            account: Account,
            startBalance: Long?,
        ): Outcome<Unit, AccountError> {
            val now = clock.millis()
            return tx.write {
                val old = repository.getAccount(account.id)
                val error = AccountRules.checkUpdate(account)
                when {
                    old == null || old.deletedAt != null -> {
                        Outcome.Err(AccountError.NotFound)
                    }

                    error != null -> {
                        Outcome.Err(error)
                    }

                    else -> {
                        repository.update(account.copy(updatedAt = now))
                        account.bankPackageName?.let { repository.setBankPackage(account.id, it, now) }
                        if (!old.isInitialized && startBalance != null && startBalance != old.initialBalance) {
                            repository.setInitialBalance(account.id, startBalance, now)
                        }
                        Outcome.Ok(Unit)
                    }
                }
            }
        }
    }
