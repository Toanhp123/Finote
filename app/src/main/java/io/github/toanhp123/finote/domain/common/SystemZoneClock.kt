package io.github.toanhp123.finote.domain.common

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Unlike `Clock.systemDefaultZone()`, which pins the zone at creation, this follows a mid-session zone change. */
internal class SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun instant(): Instant = Instant.now()

    override fun withZone(zone: ZoneId): Clock = system(zone)
}
