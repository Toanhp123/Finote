package io.github.toanhp123.finote.data

import androidx.room3.Database
import androidx.room3.RoomDatabase
import io.github.toanhp123.finote.data.account.AccountBalanceSnapshotDao
import io.github.toanhp123.finote.data.account.AccountBalanceSnapshotEntity
import io.github.toanhp123.finote.data.account.AccountDao
import io.github.toanhp123.finote.data.account.AccountEntity
import io.github.toanhp123.finote.data.account.AccountMergeDao
import io.github.toanhp123.finote.data.category.CategoryDao
import io.github.toanhp123.finote.data.category.CategoryEntity
import io.github.toanhp123.finote.data.notification.NotificationLogDao
import io.github.toanhp123.finote.data.notification.NotificationLogEntity
import io.github.toanhp123.finote.data.notification.ParserRuleDao
import io.github.toanhp123.finote.data.notification.ParserRuleEntity
import io.github.toanhp123.finote.data.transaction.TagDao
import io.github.toanhp123.finote.data.transaction.TagEntity
import io.github.toanhp123.finote.data.transaction.TransactionDao
import io.github.toanhp123.finote.data.transaction.TransactionEntity
import io.github.toanhp123.finote.data.transaction.TransactionTagCrossRef

@Database(
    entities =
        [
            AccountEntity::class,
            AccountBalanceSnapshotEntity::class,
            CategoryEntity::class,
            TransactionEntity::class,
            TagEntity::class,
            TransactionTagCrossRef::class,
            ParserRuleEntity::class,
            NotificationLogEntity::class,
        ],
    version = 7,
    exportSchema = true,
)
abstract class FinoteDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun accountMergeDao(): AccountMergeDao

    abstract fun accountBalanceSnapshotDao(): AccountBalanceSnapshotDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun tagDao(): TagDao

    abstract fun parserRuleDao(): ParserRuleDao

    abstract fun notificationLogDao(): NotificationLogDao
}
