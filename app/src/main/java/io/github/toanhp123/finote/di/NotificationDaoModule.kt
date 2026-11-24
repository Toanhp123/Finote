package io.github.toanhp123.finote.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.notification.local.dao.NotificationLogDao
import io.github.toanhp123.finote.data.notification.local.dao.ParserRuleDao
import io.github.toanhp123.finote.data.notification.local.dao.UserIgnoreKeywordDao

/** The notification feature's DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object NotificationDaoModule {
    @Provides
    fun provideParserRuleDao(db: FinoteDatabase): ParserRuleDao = db.parserRuleDao()

    @Provides
    fun provideNotificationLogDao(db: FinoteDatabase): NotificationLogDao = db.notificationLogDao()

    @Provides
    fun provideUserIgnoreKeywordDao(db: FinoteDatabase): UserIgnoreKeywordDao = db.userIgnoreKeywordDao()
}
