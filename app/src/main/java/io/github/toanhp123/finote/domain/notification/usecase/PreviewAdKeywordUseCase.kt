package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.AdKeywordPreview
import io.github.toanhp123.finote.domain.notification.model.AdKeywordRefusal
import io.github.toanhp123.finote.domain.notification.repository.UserKeywordRepository
import io.github.toanhp123.finote.domain.notification.service.checkAdKeyword
import io.github.toanhp123.finote.domain.notification.service.countOtherHiddenItems
import javax.inject.Inject

class PreviewAdKeywordUseCase
    @Inject
    constructor(
        private val repository: UserKeywordRepository,
    ) {
        /** Reads only. The refusal [LearnAdKeywordUseCase] would give, else how many other items [keyword] hides. */
        suspend operator fun invoke(
            selectedLogId: String,
            keyword: String,
        ): AdKeywordPreview {
            val selected =
                repository.getLog(selectedLogId) ?: return AdKeywordPreview(AdKeywordRefusal.NOT_IN_SELECTED, 0)
            val logs = repository.getLogs(selected.packageName)
            val existing = repository.getKeywords(selected.packageName).map { it.normalizedKeyword }
            val refusal =
                checkAdKeyword(keyword, selected, logs, repository.getBankName(selected.packageName), existing)
            return AdKeywordPreview(refusal, if (refusal == null) countOtherHiddenItems(keyword, selected, logs) else 0)
        }
    }
