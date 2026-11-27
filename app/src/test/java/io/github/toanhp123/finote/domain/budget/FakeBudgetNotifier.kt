package io.github.toanhp123.finote.domain.budget

import io.github.toanhp123.finote.domain.budget.model.BudgetAlert
import io.github.toanhp123.finote.domain.budget.port.BudgetNotifier

class FakeBudgetNotifier : BudgetNotifier {
    val alerts = mutableListOf<BudgetAlert>()

    override fun notify(alert: BudgetAlert) {
        alerts += alert
    }
}
