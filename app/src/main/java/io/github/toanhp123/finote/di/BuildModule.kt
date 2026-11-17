package io.github.toanhp123.finote.di

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.toanhp123.finote.BuildConfig
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.ToolsEnabled

@Module
@InstallIn(SingletonComponent::class)
object BuildModule {
    @Provides
    @IsDebuggable
    fun provideIsDebuggable(
        @ApplicationContext context: Context,
    ): Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @Provides
    @ToolsEnabled
    fun provideToolsEnabled(): Boolean = BuildConfig.TOOLS_ENABLED
}
