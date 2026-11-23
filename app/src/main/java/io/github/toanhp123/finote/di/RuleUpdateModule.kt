package io.github.toanhp123.finote.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.data.notification.remote.HttpParserRuleFeed
import io.github.toanhp123.finote.data.notification.repository.RuleUpdateSettingsRepositoryImpl
import io.github.toanhp123.finote.domain.notification.port.ParserRuleFeed
import io.github.toanhp123.finote.domain.notification.port.RuleUpdateScheduler
import io.github.toanhp123.finote.domain.notification.repository.RuleUpdateSettingsRepository
import io.github.toanhp123.finote.work.WorkRuleUpdateScheduler

@Module
@InstallIn(SingletonComponent::class)
interface RuleUpdateModule {
    @Binds
    fun bindParserRuleFeed(impl: HttpParserRuleFeed): ParserRuleFeed

    @Binds
    fun bindRuleUpdateSettingsRepository(impl: RuleUpdateSettingsRepositoryImpl): RuleUpdateSettingsRepository

    @Binds
    fun bindRuleUpdateScheduler(impl: WorkRuleUpdateScheduler): RuleUpdateScheduler
}
