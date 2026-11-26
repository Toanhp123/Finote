package io.github.toanhp123.finote.domain.report.usecase

import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.domain.report.model.ReportRows
import io.github.toanhp123.finote.domain.report.service.ReportCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

/** The rows behind one [ReportFilter] of [month]. */
class ObserveReportRowsUseCase
    @Inject
    constructor(
        private val input: ObserveReportInputUseCase,
    ) {
        operator fun invoke(
            month: YearMonth,
            filter: ReportFilter,
        ): Flow<ReportRows> = input(month, month).map { ReportCalculator.rows(it, month, filter) }
    }
