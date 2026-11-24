package io.github.toanhp123.finote.data.account.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import io.github.toanhp123.finote.data.account.local.entity.AccountIdentifierEntity
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountIdentifierDao {
    @Query("SELECT * FROM account_identifiers")
    suspend fun getAll(): List<AccountIdentifierEntity>

    @Query("SELECT * FROM account_identifiers WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): List<AccountIdentifierEntity>

    @Query("SELECT * FROM account_identifiers WHERE packageName = :packageName AND kind = :kind AND mask = :mask")
    suspend fun get(
        packageName: String,
        kind: IdentifierKind,
        mask: String,
    ): AccountIdentifierEntity?

    /** Throws when the key already has an owner. */
    @Insert
    suspend fun insert(identifier: AccountIdentifierEntity)

    @Query(
        "UPDATE account_identifiers SET accountId = :accountId " +
            "WHERE packageName = :packageName AND kind = :kind AND mask = :mask",
    )
    suspend fun repoint(
        packageName: String,
        kind: IdentifierKind,
        mask: String,
        accountId: String,
    ): Int

    @Query("SELECT * FROM account_identifiers WHERE accountId = :accountId ORDER BY kind, mask")
    fun observeByAccount(accountId: String): Flow<List<AccountIdentifierEntity>>

    @Query("SELECT DISTINCT accountId FROM account_identifiers")
    fun observeOwnerIds(): Flow<List<String>>

    @Query("DELETE FROM account_identifiers WHERE packageName = :packageName AND kind = :kind AND mask = :mask")
    suspend fun delete(
        packageName: String,
        kind: IdentifierKind,
        mask: String,
    ): Int

    @Query("UPDATE account_identifiers SET accountId = :to WHERE accountId = :from")
    suspend fun moveAll(
        from: String,
        to: String,
    ): Int
}
