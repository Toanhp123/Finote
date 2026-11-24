package io.github.toanhp123.finote.data.account.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import io.github.toanhp123.finote.data.account.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC, id ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT MAX(sortOrder) FROM accounts")
    suspend fun getMaxSortOrder(): Int?

    @Query("SELECT * FROM accounts WHERE bankPackageName IS NOT NULL")
    suspend fun getWithBankKey(): List<AccountEntity>

    @Query("UPDATE accounts SET isPrimary = 0, updatedAt = :now WHERE isPrimary = 1 AND id != :id")
    suspend fun clearPrimaryExcept(
        id: String,
        now: Long,
    )

    @Query("UPDATE accounts SET sortOrder = :sortOrder, updatedAt = :now WHERE id = :id")
    suspend fun setSortOrder(
        id: String,
        sortOrder: Int,
        now: Long,
    )
}
