package com.chzzz.live

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface LiveBroadcastRepository : JpaRepository<LiveBroadcast, Long> {
    // 목록 응답에 소유자 닉네임이 들어가므로 소유자를 한 번에 함께 읽는다.
    @EntityGraph(attributePaths = ["owner"])
    fun findAllByStatusOrderByStartedAtDescIdDesc(status: LiveBroadcastStatus): List<LiveBroadcast>

    @EntityGraph(attributePaths = ["owner"])
    fun findTop10ByStatusOrderByEndedAtDescIdDesc(status: LiveBroadcastStatus): List<LiveBroadcast>

    fun findAllByStatusIn(statuses: Collection<LiveBroadcastStatus>): List<LiveBroadcast>

    fun findAllByStatusAndCreatedAtBefore(status: LiveBroadcastStatus, createdAt: Instant): List<LiveBroadcast>
}
