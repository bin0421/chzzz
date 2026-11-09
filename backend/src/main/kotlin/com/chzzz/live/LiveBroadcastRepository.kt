package com.chzzz.live

import org.springframework.data.jpa.repository.JpaRepository

interface LiveBroadcastRepository : JpaRepository<LiveBroadcast, Long> {
    fun findAllByStatusOrderByStartedAtDescIdDesc(status: LiveBroadcastStatus): List<LiveBroadcast>

    fun findTop10ByStatusOrderByEndedAtDescIdDesc(status: LiveBroadcastStatus): List<LiveBroadcast>

    fun findAllByStatusIn(statuses: Collection<LiveBroadcastStatus>): List<LiveBroadcast>
}
