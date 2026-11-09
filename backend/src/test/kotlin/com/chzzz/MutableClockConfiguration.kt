package com.chzzz

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 앱의 `Clock` 빈 대신 테스트가 시각을 움직일 수 있는 시계를 끼운다. */
@TestConfiguration(proxyBeanMethods = false)
class MutableClockConfiguration {
    @Bean
    @Primary
    fun mutableClock(): MutableClock = MutableClock()
}

class MutableClock(
    @Volatile private var now: Instant = Instant.parse("2026-01-01T00:00:00Z"),
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now

    fun setTo(instant: Instant) {
        now = instant
    }

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}
