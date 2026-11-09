package io.github.toanhp123.finote.data.transaction

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.toanhp123.finote.data.account.AccountEntity
import io.github.toanhp123.finote.data.category.CategoryEntity
import io.github.toanhp123.finote.data.notification.NotificationLogEntity
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

// No single-column accountId / toAccountId index: the composites below already lead with them.
@Entity(
    tableName = "transactions",
    foreignKeys =
        [
            ForeignKey(
                entity = AccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = AccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["toAccountId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = NotificationLogEntity::class,
                parentColumns = ["id"],
                childColumns = ["notificationLogId"],
                onDelete = ForeignKey.SET_NULL,
            ),
            ForeignKey(
                entity = CategoryEntity::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index(value = ["accountId", "timestamp"]),
            Index(value = ["toAccountId", "timestamp"]),
            Index(value = ["categoryId"]),
            Index(value = ["timestamp"]),
            Index(value = ["notificationLogId"]),
        ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    /** Positive VND. Validated in the domain layer. */
    val amount: Long,
    /** Always charged to [accountId]. */
    @ColumnInfo(defaultValue = "0") val feeAmount: Long,
    val type: TransactionType,
    /** Only set when [type] is ADJUSTMENT. */
    val adjustmentDirection: AdjustmentDirection?,
    val accountId: String,
    /** Required when [type] is TRANSFER. */
    val toAccountId: String?,
    val categoryId: String?,
    val timestamp: Long,
    val merchantName: String?,
    val note: String,
    val source: TransactionSource,
    // Column name follows the spec; the property carries the `is` prefix the naming rule demands.
    @ColumnInfo(name = "excludeFromAnalytics") val isExcludedFromAnalytics: Boolean,
    @ColumnInfo(defaultValue = "NONE") val reimbursementStatus: ReimbursementStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    /** VND balance the bank printed after this transaction; null for a manual one. */
    val postBalance: Long?,
    /** The notification this transaction came from. Cleared when the log row is purged. */
    val notificationLogId: String?,
)
