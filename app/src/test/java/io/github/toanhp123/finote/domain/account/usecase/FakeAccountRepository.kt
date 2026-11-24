package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.common.StorageException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Copies from the real repository: `observeAccounts` hides deleted rows and sorts by sortOrder, createdAt, id;
 * `update` keeps only display fields; archive, ignore and delete clear the star.
 * Does not copy: trash effects, balance rebuilds from transactions. Business rules live in `AccountRules`, not in the
 * repository.
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
        val old = checkNotNull(stored[id])
        stored[id] = old.copy(isArchived = archived, isPrimary = old.isPrimary && !archived, updatedAt = updatedAt)
        notifyChanged()
    }

    override suspend fun softDelete(
        id: String,
        deletedAt: Long,
    ) {
        writes++
        stored[id] = checkNotNull(stored[id]).copy(deletedAt = deletedAt, isPrimary = false, updatedAt = deletedAt)
        notifyChanged()
    }

    /** What [getLiveTransactionCount] answers per account id; 0 when absent. */
    val liveCounts = mutableMapOf<String, Int>()

    override suspend fun getLiveTransactionCount(id: String): Int = liveCounts[id] ?: 0

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
        stored[id]?.let {
            stored[id] =
                it.copy(isIgnored = ignored, isPrimary = it.isPrimary && !ignored, updatedAt = now)
        }
        notifyChanged()
    }

    override suspend fun getTrashableTransactionCount(id: String): Int = trashableCounts[id] ?: 0

    override suspend fun getNextSortOrder(): Int = (stored.values.maxOfOrNull { it.sortOrder } ?: -1) + 1

    /** The fake has no transactions, so the balance moves by the change of the start. */
    override suspend fun setInitialBalance(
        id: String,
        initialBalance: Long,
        now: Long,
    ) {
        writes++
        stored[id]?.let {
            stored[id] =
                it.copy(
                    initialBalance = initialBalance,
                    currentBalance = it.currentBalance + initialBalance - it.initialBalance,
                    isInitialized = true,
                    updatedAt = now,
                )
        }
        notifyChanged()
    }

    override suspend fun setPrimary(
        id: String,
        now: Long,
    ) {
        writes++
        if (id !in stored) return
        stored.replaceAll { key, account ->
            if (key == id) {
                account.copy(isPrimary = true, updatedAt = now)
            } else if (account.isPrimary) {
                account.copy(isPrimary = false, updatedAt = now)
            } else {
                account
            }
        }
        notifyChanged()
    }

    override suspend fun clearPrimary(
        id: String,
        now: Long,
    ) {
        writes++
        stored[id]?.takeIf { it.isPrimary }?.let { stored[id] = it.copy(isPrimary = false, updatedAt = now) }
        notifyChanged()
    }

    override suspend fun reorder(
        orderedIds: List<String>,
        now: Long,
    ) {
        writes++
        orderedIds.forEachIndexed { index, id ->
            stored[id]?.let { stored[id] = it.copy(sortOrder = index, updatedAt = now) }
        }
        notifyChanged()
    }

    override suspend fun setBankPackage(
        id: String,
        packageName: String,
        now: Long,
    ) {
        writes++
        stored[id]
            ?.takeIf { it.bankPackageName == null }
            ?.let { stored[id] = it.copy(bankPackageName = packageName, updatedAt = now) }
        notifyChanged()
    }

    /** The identifier rows. A test may edit it, then call [notifyChanged]. */
    val identifiers = mutableListOf<AccountIdentifier>()

    override fun observeIdentifiers(accountId: String): Flow<List<AccountIdentifier>> =
        version.map { _ -> identifiers.filter { it.accountId == accountId } }

    override fun observeIdentifiedAccountIds(): Flow<Set<String>> =
        version.map { _ -> identifiers.mapTo(mutableSetOf()) { it.accountId } }

    override suspend fun getIdentifier(
        packageName: String,
        kind: IdentifierKind,
        mask: String,
    ): AccountIdentifier? = identifiers.firstOrNull { it.matches(packageName, kind, mask) }

    /** Same rule as the real repository: a key of a deleted account moves over, a key of a live one throws. */
    override suspend fun addIdentifier(identifier: AccountIdentifier) {
        writes++
        val current = getIdentifier(identifier.packageName, identifier.kind, identifier.mask)
        if (current != null) {
            if (stored[current.accountId]?.deletedAt == null) {
                throw StorageException(IllegalStateException("key owned by a live account"))
            }
            identifiers.remove(current)
        }
        identifiers += identifier
        notifyChanged()
    }

    override suspend fun removeIdentifier(identifier: AccountIdentifier) {
        writes++
        identifiers.removeAll { it.matches(identifier.packageName, identifier.kind, identifier.mask) }
        notifyChanged()
    }

    private fun AccountIdentifier.matches(
        packageName: String,
        kind: IdentifierKind,
        mask: String,
    ) = this.packageName == packageName && this.kind == kind && this.mask == mask
}
