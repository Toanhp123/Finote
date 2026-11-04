package io.github.toanhp123.finote.data.transaction

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    /** Live rows that move this account's balance: it is the source, or the destination of a transfer. */
    @Query(
        "SELECT * FROM transactions WHERE deletedAt IS NULL AND (accountId = :accountId OR toAccountId = :accountId)",
    )
    suspend fun getActiveByAccount(accountId: String): List<TransactionEntity>

    /** Live rows that touch this account, as source or as a transfer destination. */
    @Query(
        "SELECT COUNT(*) FROM transactions " +
            "WHERE deletedAt IS NULL AND (accountId = :accountId OR toAccountId = :accountId)",
    )
    suspend fun countActiveByAccount(accountId: String): Int

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY timestamp DESC, createdAt DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    /** Account of the newest live manual entry; null when there is none. */
    @Query(
        "SELECT accountId FROM transactions " +
            "WHERE deletedAt IS NULL AND source = 'MANUAL' ORDER BY createdAt DESC, id DESC LIMIT 1",
    )
    suspend fun getLastManualAccountId(): String?

    /** Category of the newest live manual entry of this [type] that has one. */
    @Query(
        "SELECT categoryId FROM transactions WHERE deletedAt IS NULL AND source = 'MANUAL' " +
            "AND type = :type AND categoryId IS NOT NULL ORDER BY createdAt DESC, id DESC LIMIT 1",
    )
    suspend fun getLastManualCategoryId(type: TransactionType): String?

    @Insert
    suspend fun insertTagRef(ref: TransactionTagCrossRef)

    @Delete
    suspend fun deleteTagRef(ref: TransactionTagCrossRef)

    @Query("SELECT * FROM transaction_tag_cross_ref WHERE transactionId = :transactionId")
    suspend fun getTagRefs(transactionId: String): List<TransactionTagCrossRef>
}
