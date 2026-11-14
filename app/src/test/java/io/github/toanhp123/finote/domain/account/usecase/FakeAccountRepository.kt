package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Copies from the real repository: `observeAccounts` hides deleted rows and sorts by sortOrder, createdAt, id;
 * `update` keeps only display fields.
 * Does not copy: merge and trash effects. Business rules live in `AccountRules`, not in the repository.
 */
class FakeAccountRepository : AccountRepository {
    val stored = mutableMapOf<String, Account>()
    private val version = MutableStateFlow(0)
    var writes = 0
        private set

    /** Makes [observeAccounts] emit again after a test edits [stored] directly. */
    fun notifyChanged() {
        version.value++
    }

    override suspend fun getAccount(id: String): Account? = stored[id]

    override fun observeAccounts(): Flow<List<Account>> =
        version.map { _ ->
            stored.values
                .filter { account -> account.deletedAt == null }
                .sortedWith(compareBy<Account> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
        }

    override suspend fun add(account: Account) {
        writes++
        stored[account.id] = account
        notifyChanged()
    }

    /** The argument of the last [update], before the contract drops machine-owned fields. */
    var lastUpdateArg: Account? = null
        private set

    /** Same contract as the real repository: only display fields come from [account]. */
    override suspend fun update(account: Account) {
        writes++
        lastUpdateArg = account
        val old = checkNotNull(stored[account.id])
        stored[account.id] =
            old.copy(
                name = account.name,
                nameKey = if (account.name == old.name) old.nameKey else null,
                icon = account.icon,
                color = account.color,
                sortOrder = account.sortOrder,
                isExcludedFromNetWorth = account.isExcludedFromNetWorth,
                updatedAt = account.updatedAt,
            )
        notifyChanged()
    }

    override suspend fun setArchived(
        id: String,
        archived: Boolean,
        updatedAt: Long,
    ) {
        writes++
        stored[id] = checkNotNull(stored[id]).copy(isArchived = archived, updatedAt = updatedAt)
        notifyChanged()
    }

    override suspend fun softDelete(
        id: String,
        deletedAt: Long,
    ) {
        writes++
        stored[id] = checkNotNull(stored[id]).copy(deletedAt = deletedAt, updatedAt = deletedAt)
        notifyChanged()
    }

    /** Each merge call as (sourceId, targetId, now). The fake moves nothing. */
    val merges = mutableListOf<Triple<String, String, Long>>()

    /** What [getLiveTransactionCount] answers per account id; 0 when absent. */
    val liveCounts = mutableMapOf<String, Int>()

    override suspend fun merge(
        sourceId: String,
        targetId: String,
        now: Long,
    ) {
        writes++
        merges += Triple(sourceId, targetId, now)
    }

    override suspend fun getLiveTransactionCount(id: String): Int = liveCounts[id] ?: 0

    /** What [countTransfersBetween] answers per unordered pair of ids; 0 when absent. */
    val transferCounts = mutableMapOf<Set<String>, Int>()

    override suspend fun countTransfersBetween(
        sourceId: String,
        targetId: String,
    ): Int = transferCounts[setOf(sourceId, targetId)] ?: 0

    data class IgnoreCall(
        val id: String,
        val ignored: Boolean,
        val trash: Boolean,
        val now: Long,
    )

    /** Each setIgnored call. The fake trashes nothing. */
    val ignores = mutableListOf<IgnoreCall>()

    /** What [getTrashableTransactionCount] answers per account id; 0 when absent. */
    val trashableCounts = mutableMapOf<String, Int>()

    override suspend fun setIgnored(
        id: String,
        ignored: Boolean,
        shouldTrashOldTransactions: Boolean,
        now: Long,
    ) {
        writes++
        ignores += IgnoreCall(id, ignored, shouldTrashOldTransactions, now)
        stored[id]?.let { stored[id] = it.copy(isIgnored = ignored, updatedAt = now) }
        notifyChanged()
    }

    override suspend fun getTrashableTransactionCount(id: String): Int = trashableCounts[id] ?: 0
}
