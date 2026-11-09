package com.chzzz.live

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface LiveBroadcastRepository : JpaRepository<LiveBroadcast, Long> {
    fun findAllByStatusOrderByStartedAtDescIdDesc(status: LiveBroadcastStatus): List<LiveBroadcast>

    fun findAllByStatusAndCreatedAtBefore(status: LiveBroadcastStatus, createdAt: Instant): List<LiveBroadcast>
}
