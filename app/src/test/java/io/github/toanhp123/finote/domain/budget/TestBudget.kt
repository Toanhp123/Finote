package io.github.toanhp123.finote.domain.budget

import io.github.toanhp123.finote.domain.budget.model.Budget
import java.time.YearMonth

// Test fixture: every field has a default so call sites name only what matters.
@Suppress("LongParameterList")
internal fun testBudget(
    id: String,
    month: YearMonth,
    categoryId: String? = null,
    limit: Long = 1_000_000,
    isRollover: Boolean = false,
    threshold: Int = 80,
    mark: Int? = null,
) = Budget(id, categoryId, limit, month, isRollover, threshold, mark, createdAt = 1L, updatedAt = 1L)
