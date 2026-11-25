package io.github.toanhp123.finote.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.data.account.repository.AccountMergeRepositoryImpl
import io.github.toanhp123.finote.data.account.repository.AccountRepositoryImpl
import io.github.toanhp123.finote.data.account.repository.DriftAcknowledgementRepositoryImpl
import io.github.toanhp123.finote.data.account.repository.MergeDismissalRepositoryImpl
import io.github.toanhp123.finote.data.category.repository.CategoryRepositoryImpl
import io.github.toanhp123.finote.data.category.repository.KeywordRuleRepositoryImpl
import io.github.toanhp123.finote.data.database.RoomTransactionRunner
import io.github.toanhp123.finote.data.setup.repository.SetupRepositoryImpl
import io.github.toanhp123.finote.data.transaction.repository.TransactionRepositoryImpl
import io.github.toanhp123.finote.data.transaction.repository.TransferPairRepositoryImpl
import io.github.toanhp123.finote.domain.account.repository.AccountMergeRepository
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.repository.DriftAcknowledgementRepository
import io.github.toanhp123.finote.domain.account.repository.MergeDismissalRepository
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import io.github.toanhp123.finote.domain.common.TransactionRunner
import io.github.toanhp123.finote.domain.setup.repository.SetupRepository
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import io.github.toanhp123.finote.domain.transaction.repository.TransferPairRepository

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    fun bindAccountMergeRepository(impl: AccountMergeRepositoryImpl): AccountMergeRepository

    @Binds
    fun bindDriftAcknowledgementRepository(impl: DriftAcknowledgementRepositoryImpl): DriftAcknowledgementRepository

    @Binds
    fun bindMergeDismissalRepository(impl: MergeDismissalRepositoryImpl): MergeDismissalRepository

    @Binds
    fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    fun bindKeywordRuleRepository(impl: KeywordRuleRepositoryImpl): KeywordRuleRepository

    @Binds
    fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    fun bindTransferPairRepository(impl: TransferPairRepositoryImpl): TransferPairRepository

    @Binds
    fun bindSetupRepository(impl: SetupRepositoryImpl): SetupRepository

    @Binds
    fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner
}
