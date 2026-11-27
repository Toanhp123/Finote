package io.github.toanhp123.finote.data.budget.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.toanhp123.finote.data.category.local.entity.CategoryEntity

/**
 * A monthly spending limit. [categoryId] null is the total budget. [monthYear] is `yyyy-MM`.
 * [lastAlertSentThreshold] is the highest percent already alerted this month, null before any alert.
 * Plans, not money records: rows are hard-deleted. SQLite treats NULLs as distinct in a unique index, so the
 * one-total-per-month rule is not enforced here.
 */
@Entity(
    tableName = "budgets",
    foreignKeys =
        [
            ForeignKey(
                entity = CategoryEntity::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index(value = ["monthYear", "categoryId"], unique = true), Index(value = ["categoryId"])],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val categoryId: String?,
    val limitAmount: Long,
    val monthYear: String,
    val isRollover: Boolean,
    val alertThreshold: Int,
    val lastAlertSentThreshold: Int?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    // The limit is a financial figure: keep it out of logs and crash reports.
    override fun toString() = "BudgetEntity(id=$id, categoryId=$categoryId, monthYear=$monthYear)"
}
