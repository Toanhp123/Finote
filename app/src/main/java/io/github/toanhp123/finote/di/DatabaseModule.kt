package io.github.toanhp123.finote.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.data.FinoteDatabase
import io.github.toanhp123.finote.data.account.AccountBalanceSnapshotDao
import io.github.toanhp123.finote.data.account.AccountDao
import io.github.toanhp123.finote.data.category.CategoryDao
import io.github.toanhp123.finote.data.transaction.TagDao
import io.github.toanhp123.finote.data.transaction.TransactionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): FinoteDatabase =
        Room
            .databaseBuilder<FinoteDatabase>(context, "finote.db")
            .setDriver(AndroidSQLiteDriver())
            .build()

    @Provides
    fun provideAccountDao(db: FinoteDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideAccountBalanceSnapshotDao(db: FinoteDatabase): AccountBalanceSnapshotDao = db.accountBalanceSnapshotDao()

    @Provides
    fun provideCategoryDao(db: FinoteDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: FinoteDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideTagDao(db: FinoteDatabase): TagDao = db.tagDao()
}
