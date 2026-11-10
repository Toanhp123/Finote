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

    @Query("SELECT * FROM accounts WHERE bankPackageName IS NOT NULL")
    suspend fun getWithBankKey(): List<AccountEntity>

    @Query(
        "SELECT * FROM accounts WHERE deletedAt IS NULL AND bankPackageName = :packageName " +
            "AND accountNumberMask IS :mask LIMIT 1",
    )
    suspend fun getByBankKey(
        packageName: String,
        mask: String?,
    ): AccountEntity?
}
