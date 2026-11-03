package io.github.toanhp123.finote.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.data.account.AccountRepositoryImpl
import io.github.toanhp123.finote.data.category.CategoryRepositoryImpl
import io.github.toanhp123.finote.data.transaction.TransactionRepositoryImpl
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
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
}
