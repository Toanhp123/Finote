package io.github.toanhp123.finote.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    fun provideClock(): Clock = SystemZoneClock()
}

/** Unlike `Clock.systemDefaultZone()`, which pins the zone at creation, this follows a mid-session zone change. */
internal class SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun instant(): Instant = Instant.now()

    override fun withZone(zone: ZoneId): Clock = system(zone)
}
