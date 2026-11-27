package io.github.toanhp123.finote.domain.budget.service

import io.github.toanhp123.finote.domain.report.model.MonthReport
import io.github.toanhp123.finote.domain.report.model.ReportInput
import io.github.toanhp123.finote.domain.report.service.ReportCalculator
import java.time.LocalDate
import java.time.YearMonth

/** The month reports `BudgetMath.statuses` reads: the same numbers as Overview, one per month. */
object BudgetReports {
    fun between(
        input: ReportInput,
        from: YearMonth,
        to: YearMonth,
        today: LocalDate,
    ): Map<YearMonth, MonthReport> =
        generateSequence(from) { it.plusMonths(1) }
            .takeWhile { it <= to }
            .associateWith { ReportCalculator.monthReport(input, it, today) }
}
