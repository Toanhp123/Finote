package io.github.toanhp123.finote.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.domain.notification.port.SettleScheduler
import io.github.toanhp123.finote.work.WorkSettleScheduler

@Module
@InstallIn(SingletonComponent::class)
interface SettleModule {
    @Binds
    fun bindSettleScheduler(impl: WorkSettleScheduler): SettleScheduler
}
