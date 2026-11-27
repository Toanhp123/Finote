package io.github.toanhp123.finote.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.domain.budget.port.BudgetNotifier
import io.github.toanhp123.finote.ui.common.notification.AndroidBudgetNotifier

@Module
@InstallIn(SingletonComponent::class)
interface BudgetModule {
    @Binds
    fun bindBudgetNotifier(impl: AndroidBudgetNotifier): BudgetNotifier
}
