package com.chzzz.live

import org.springframework.data.jpa.repository.JpaRepository

interface LiveBroadcastRepository : JpaRepository<LiveBroadcast, Long> {
    fun findAllByStatusOrderByStartedAtDescIdDesc(status: LiveBroadcastStatus): List<LiveBroadcast>
}
