package io.github.toanhp123.finote.data.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import io.github.toanhp123.finote.data.account.local.dao.AccountBalanceSnapshotDao
import io.github.toanhp123.finote.data.account.local.dao.AccountDao
import io.github.toanhp123.finote.data.account.local.dao.AccountIdentifierDao
import io.github.toanhp123.finote.data.account.local.dao.AccountMergeDao
import io.github.toanhp123.finote.data.account.local.entity.AccountBalanceSnapshotEntity
import io.github.toanhp123.finote.data.account.local.entity.AccountEntity
import io.github.toanhp123.finote.data.account.local.entity.AccountIdentifierEntity
import io.github.toanhp123.finote.data.category.local.dao.CategoryDao
import io.github.toanhp123.finote.data.category.local.entity.CategoryEntity
import io.github.toanhp123.finote.data.notification.local.dao.NotificationLogDao
import io.github.toanhp123.finote.data.notification.local.dao.ParserRuleDao
import io.github.toanhp123.finote.data.notification.local.dao.UserIgnoreKeywordDao
import io.github.toanhp123.finote.data.notification.local.entity.NotificationLogEntity
import io.github.toanhp123.finote.data.notification.local.entity.ParserRuleEntity
import io.github.toanhp123.finote.data.notification.local.entity.UserIgnoreKeywordEntity
import io.github.toanhp123.finote.data.transaction.local.dao.TagDao
import io.github.toanhp123.finote.data.transaction.local.dao.TransactionDao
import io.github.toanhp123.finote.data.transaction.local.dao.TransferPairDao
import io.github.toanhp123.finote.data.transaction.local.entity.TagEntity
import io.github.toanhp123.finote.data.transaction.local.entity.TransactionEntity
import io.github.toanhp123.finote.data.transaction.local.entity.TransactionTagCrossRef

/** Schema version. Raising it needs a new `app/schemas/.../N.json` and a migration; see `MigrationPolicy.kt`. */
const val DATABASE_VERSION = 12

@Database(
    entities =
        [
            AccountEntity::class,
            AccountIdentifierEntity::class,
            AccountBalanceSnapshotEntity::class,
            CategoryEntity::class,
            TransactionEntity::class,
            TagEntity::class,
            TransactionTagCrossRef::class,
            ParserRuleEntity::class,
            NotificationLogEntity::class,
            UserIgnoreKeywordEntity::class,
        ],
    version = DATABASE_VERSION,
    exportSchema = true,
)
abstract class FinoteDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun accountIdentifierDao(): AccountIdentifierDao

    abstract fun accountMergeDao(): AccountMergeDao

    abstract fun accountBalanceSnapshotDao(): AccountBalanceSnapshotDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun transferPairDao(): TransferPairDao

    abstract fun tagDao(): TagDao

    abstract fun parserRuleDao(): ParserRuleDao

    abstract fun notificationLogDao(): NotificationLogDao

    abstract fun userIgnoreKeywordDao(): UserIgnoreKeywordDao
}
