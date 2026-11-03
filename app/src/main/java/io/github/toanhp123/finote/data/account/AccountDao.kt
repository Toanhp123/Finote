package io.github.toanhp123.finote.data.account

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>
}
