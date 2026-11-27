package io.github.toanhp123.finote.data.budget.mapper

import io.github.toanhp123.finote.data.budget.local.entity.BudgetEntity
import io.github.toanhp123.finote.domain.budget.model.Budget
import java.time.YearMonth

fun BudgetEntity.toDomain(): Budget =
    Budget(
        id = id,
        categoryId = categoryId,
        limitAmount = limitAmount,
        month = YearMonth.parse(monthYear),
        isRollover = isRollover,
        alertThreshold = alertThreshold,
        lastAlertSentThreshold = lastAlertSentThreshold,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Budget.toEntity(): BudgetEntity =
    BudgetEntity(
        id = id,
        categoryId = categoryId,
        limitAmount = limitAmount,
        monthYear = month.toString(),
        isRollover = isRollover,
        alertThreshold = alertThreshold,
        lastAlertSentThreshold = lastAlertSentThreshold,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
