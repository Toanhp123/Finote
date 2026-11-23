package io.github.toanhp123.finote.data.account.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import io.github.toanhp123.finote.data.account.local.entity.AccountBalanceSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountBalanceSnapshotDao {
    @Insert
    suspend fun insert(snapshot: AccountBalanceSnapshotEntity)

    @Update
    suspend fun update(snapshot: AccountBalanceSnapshotEntity)

    @Query("SELECT * FROM account_balance_snapshots WHERE id = :id")
    suspend fun getById(id: String): AccountBalanceSnapshotEntity?

    @Query("DELETE FROM account_balance_snapshots WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)

    @Query("SELECT * FROM account_balance_snapshots WHERE accountId = :accountId ORDER BY monthYear ASC")
    suspend fun getByAccount(accountId: String): List<AccountBalanceSnapshotEntity>

    @Query("SELECT * FROM account_balance_snapshots WHERE accountId = :accountId ORDER BY monthYear ASC")
    fun observeByAccount(accountId: String): Flow<List<AccountBalanceSnapshotEntity>>
}
