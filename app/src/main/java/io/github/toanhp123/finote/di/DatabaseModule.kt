package io.github.toanhp123.finote.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.data.account.local.dao.AccountBalanceSnapshotDao
import io.github.toanhp123.finote.data.account.local.dao.AccountDao
import io.github.toanhp123.finote.data.account.local.dao.AccountIdentifierDao
import io.github.toanhp123.finote.data.account.local.dao.AccountMergeDao
import io.github.toanhp123.finote.data.category.local.dao.CategoryDao
import io.github.toanhp123.finote.data.category.local.dao.CategoryKeywordRuleDao
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.createFinoteDatabase
import io.github.toanhp123.finote.data.transaction.local.dao.TagDao
import io.github.toanhp123.finote.data.transaction.local.dao.TransactionDao
import io.github.toanhp123.finote.data.transaction.local.dao.TransferPairDao
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        clock: Clock,
    ): FinoteDatabase = createFinoteDatabase(context, clock)

    @Provides
    fun provideAccountDao(db: FinoteDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideAccountIdentifierDao(db: FinoteDatabase): AccountIdentifierDao = db.accountIdentifierDao()

    @Provides
    fun provideAccountMergeDao(db: FinoteDatabase): AccountMergeDao = db.accountMergeDao()

    @Provides
    fun provideAccountBalanceSnapshotDao(db: FinoteDatabase): AccountBalanceSnapshotDao = db.accountBalanceSnapshotDao()

    @Provides
    fun provideCategoryDao(db: FinoteDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideKeywordRuleDao(db: FinoteDatabase): CategoryKeywordRuleDao = db.keywordRuleDao()

    @Provides
    fun provideTransactionDao(db: FinoteDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideTransferPairDao(db: FinoteDatabase): TransferPairDao = db.transferPairDao()

    @Provides
    fun provideTagDao(db: FinoteDatabase): TagDao = db.tagDao()
}
