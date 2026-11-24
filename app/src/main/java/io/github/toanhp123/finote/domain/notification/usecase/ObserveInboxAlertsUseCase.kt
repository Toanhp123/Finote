package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.InboxAlerts
import io.github.toanhp123.finote.domain.notification.repository.InboxMarkerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

class ObserveInboxAlertsUseCase
    @Inject
    constructor(
        private val observeInbox: ObserveFailedInboxUseCase,
        private val markers: InboxMarkerRepository,
    ) {
        /** Only shown items count. The dot and the banner each wait for a shown id their marker set does not hold. */
        operator fun invoke(): Flow<InboxAlerts> =
            combine(observeInbox(), markers.observe()) { items, marked ->
                val shownIds = items.filterNot { it.isHidden }.map { it.id }
                InboxAlerts(
                    shownCount = shownIds.size,
                    hasUnseen = shownIds.any { it !in marked.seenIds },
                    isBannerVisible = shownIds.any { it !in marked.bannerDismissedIds },
                )
            }.distinctUntilChanged()
    }
