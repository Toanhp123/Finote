package io.github.toanhp123.finote.data.account.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "account_balance_snapshots",
    foreignKeys =
        [
            ForeignKey(
                entity = AccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    // The unique index also serves lookups by accountId.
    indices = [Index(value = ["accountId", "monthYear"], unique = true)],
)
data class AccountBalanceSnapshotEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val monthYear: String,
    val openingBalance: Long,
    val closingBalance: Long,
    val totalIncome: Long,
    val totalExpense: Long,
    val totalTransferIn: Long,
    val totalTransferOut: Long,
    val updatedAt: Long,
)
