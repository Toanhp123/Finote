package io.github.toanhp123.finote.data.budget.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** A month set up by the user or a copy-forward. It stays set up with no budgets, so it is not copied again. */
@Entity(tableName = "budget_months")
data class BudgetMonthEntity(
    @PrimaryKey val monthYear: String,
)
