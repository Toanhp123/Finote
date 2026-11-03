package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory stand-in. It does no business checks: those belong to the real repository. */
class FakeAccountRepository : AccountRepository {
    val stored = mutableMapOf<String, Account>()
    var writes = 0
        private set

    override suspend fun getAccount(id: String): Account? = stored[id]

    override fun observeAccounts(): Flow<List<Account>> = flowOf(stored.values.filter { it.deletedAt == null })

    override suspend fun add(account: Account) {
        writes++
        stored[account.id] = account
    }

    override suspend fun update(account: Account) {
        writes++
        stored[account.id] = account
    }

    override suspend fun setArchived(
        id: String,
        archived: Boolean,
        updatedAt: Long,
    ) {
        writes++
        stored[id] = checkNotNull(stored[id]).copy(isArchived = archived, updatedAt = updatedAt)
    }

    override suspend fun softDelete(
        id: String,
        deletedAt: Long,
    ) {
        writes++
        stored[id] = checkNotNull(stored[id]).copy(deletedAt = deletedAt, updatedAt = deletedAt)
    }
}
