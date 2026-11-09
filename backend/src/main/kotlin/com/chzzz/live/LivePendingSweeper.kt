package com.chzzz.live

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 아무도 조회하지 않아도 시간 초과된 대기가 종료로 정리되도록 주기적으로 같은 규칙을 돌린다. */
@Component
class LivePendingSweeper(
    private val service: LiveBroadcastService,
) {
    @Scheduled(fixedDelayString = "\${live.pending-sweep-interval}")
    fun sweep() {
        service.expireOverduePending()
    }
}
