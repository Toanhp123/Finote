package io.github.toanhp123.finote.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.data.notification.repository.FailedInboxRepositoryImpl
import io.github.toanhp123.finote.data.notification.repository.InboxMarkerRepositoryImpl
import io.github.toanhp123.finote.data.notification.repository.NotificationCaptureRepositoryImpl
import io.github.toanhp123.finote.data.notification.repository.NotificationSettlementRepositoryImpl
import io.github.toanhp123.finote.data.notification.repository.ParserRuleRepositoryImpl
import io.github.toanhp123.finote.data.notification.repository.UserKeywordRepositoryImpl
import io.github.toanhp123.finote.domain.notification.repository.FailedInboxRepository
import io.github.toanhp123.finote.domain.notification.repository.InboxMarkerRepository
import io.github.toanhp123.finote.domain.notification.repository.NotificationCaptureRepository
import io.github.toanhp123.finote.domain.notification.repository.NotificationSettlementRepository
import io.github.toanhp123.finote.domain.notification.repository.ParserRuleRepository
import io.github.toanhp123.finote.domain.notification.repository.UserKeywordRepository

/** The notification feature's repositories. Apart from [RepositoryModule] to stay under the detekt function limit. */
@Module
@InstallIn(SingletonComponent::class)
interface NotificationRepositoryModule {
    @Binds
    fun bindNotificationCaptureRepository(impl: NotificationCaptureRepositoryImpl): NotificationCaptureRepository

    @Binds
    fun bindNotificationSettlementRepository(
        impl: NotificationSettlementRepositoryImpl,
    ): NotificationSettlementRepository

    @Binds
    fun bindParserRuleRepository(impl: ParserRuleRepositoryImpl): ParserRuleRepository

    @Binds
    fun bindFailedInboxRepository(impl: FailedInboxRepositoryImpl): FailedInboxRepository

    @Binds
    fun bindUserKeywordRepository(impl: UserKeywordRepositoryImpl): UserKeywordRepository

    @Binds
    fun bindInboxMarkerRepository(impl: InboxMarkerRepositoryImpl): InboxMarkerRepository
}
