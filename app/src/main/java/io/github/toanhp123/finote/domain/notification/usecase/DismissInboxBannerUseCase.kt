package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.repository.InboxMarkerRepository
import javax.inject.Inject

class DismissInboxBannerUseCase
    @Inject
    constructor(
        private val repository: InboxMarkerRepository,
    ) {
        /** [ids] are the shown items now; it stores exactly them, so ids of gone items drop out. */
        suspend operator fun invoke(ids: Set<String>) = repository.dismissBanner(ids)
    }
