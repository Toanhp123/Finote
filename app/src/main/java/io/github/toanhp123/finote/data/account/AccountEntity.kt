package io.github.toanhp123.finote.data.account

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.toanhp123.finote.domain.account.model.AccountType

@Entity(tableName = "accounts", indices = [Index(value = ["bankPackageName", "accountNumberMask"])])
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameKey: String?,
    val type: AccountType,
    val initialBalance: Long,
    /** Signed. A credit card is negative while it carries debt. */
    val currentBalance: Long,
    val currency: String,
    val icon: String,
    val color: Int,
    val sortOrder: Int,
    // Column name follows the spec; the property carries the `is` prefix the naming rule demands.
    @ColumnInfo(name = "excludeFromNetWorth") val isExcludedFromNetWorth: Boolean,
    val isArchived: Boolean,
    val isIgnored: Boolean,
    val isInitialized: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    /** Package of the bank or wallet app that feeds this account; with [accountNumberMask] it is the match key. */
    val bankPackageName: String?,
    /** Last four digits of the account number, as the notifications show them. */
    val accountNumberMask: String?,
    /** Set when this account was merged into another; the target account id. */
    val mergedIntoId: String?,
)
