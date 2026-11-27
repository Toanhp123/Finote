package io.github.toanhp123.finote.domain.budget.port

import io.github.toanhp123.finote.domain.budget.model.BudgetAlert

/** Tells the user that a budget reached a mark. Posts nothing when the user turned alerts off. */
interface BudgetNotifier {
    fun notify(alert: BudgetAlert)
}
