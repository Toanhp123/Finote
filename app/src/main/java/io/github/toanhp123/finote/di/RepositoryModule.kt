package io.github.toanhp123.finote.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.data.account.repository.AccountRepositoryImpl
import io.github.toanhp123.finote.data.category.repository.CategoryRepositoryImpl
import io.github.toanhp123.finote.data.notification.repository.NotificationCaptureRepositoryImpl
import io.github.toanhp123.finote.data.notification.repository.NotificationSettlementRepositoryImpl
import io.github.toanhp123.finote.data.notification.repository.ParserRuleRepositoryImpl
import io.github.toanhp123.finote.data.setup.repository.SetupRepositoryImpl
import io.github.toanhp123.finote.data.transaction.repository.TransactionRepositoryImpl
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.notification.repository.NotificationCaptureRepository
import io.github.toanhp123.finote.domain.notification.repository.NotificationSettlementRepository
import io.github.toanhp123.finote.domain.notification.repository.ParserRuleRepository
import io.github.toanhp123.finote.domain.setup.repository.SetupRepository
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    fun bindNotificationCaptureRepository(impl: NotificationCaptureRepositoryImpl): NotificationCaptureRepository

    @Binds
    fun bindNotificationSettlementRepository(
        impl: NotificationSettlementRepositoryImpl,
    ): NotificationSettlementRepository

    @Binds
    fun bindParserRuleRepository(impl: ParserRuleRepositoryImpl): ParserRuleRepository

    @Binds
    fun bindSetupRepository(impl: SetupRepositoryImpl): SetupRepository
}
