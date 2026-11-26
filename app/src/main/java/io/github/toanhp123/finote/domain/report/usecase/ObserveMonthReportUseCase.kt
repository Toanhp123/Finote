package io.github.toanhp123.finote.domain.report.usecase

import io.github.toanhp123.finote.domain.report.model.MonthReport
import io.github.toanhp123.finote.domain.report.service.ReportCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** The report of [month]. It loads the month before too, for the spending pace. */
class ObserveMonthReportUseCase
    @Inject
    constructor(
        private val input: ObserveReportInputUseCase,
        private val clock: Clock,
    ) {
        operator fun invoke(month: YearMonth): Flow<MonthReport> =
            input(month.minusMonths(1), month).map {
                ReportCalculator.monthReport(it, month, LocalDate.now(clock))
            }
    }
