package io.github.toanhp123.finote.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.data.FinoteDatabase
import io.github.toanhp123.finote.data.account.AccountBalanceSnapshotDao
import io.github.toanhp123.finote.data.account.AccountDao
import io.github.toanhp123.finote.data.account.AccountMergeDao
import io.github.toanhp123.finote.data.category.CategoryDao
import io.github.toanhp123.finote.data.createFinoteDatabase
import io.github.toanhp123.finote.data.notification.NotificationLogDao
import io.github.toanhp123.finote.data.notification.ParserRuleDao
import io.github.toanhp123.finote.data.transaction.TagDao
import io.github.toanhp123.finote.data.transaction.TransactionDao
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
    fun provideAccountMergeDao(db: FinoteDatabase): AccountMergeDao = db.accountMergeDao()

    @Provides
    fun provideAccountBalanceSnapshotDao(db: FinoteDatabase): AccountBalanceSnapshotDao = db.accountBalanceSnapshotDao()

    @Provides
    fun provideCategoryDao(db: FinoteDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: FinoteDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideTagDao(db: FinoteDatabase): TagDao = db.tagDao()

    @Provides
    fun provideParserRuleDao(db: FinoteDatabase): ParserRuleDao = db.parserRuleDao()

    @Provides
    fun provideNotificationLogDao(db: FinoteDatabase): NotificationLogDao = db.notificationLogDao()
}
