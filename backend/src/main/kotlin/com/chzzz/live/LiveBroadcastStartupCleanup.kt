package com.chzzz.live

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 서비스가 시작될 때 대기·방송 중으로 남은 라이브 방송을 종료로 정리한다.
 * 재시작하면 MediaMTX의 연결도 끊겨 있어서, 남은 방송은 다시 이어질 수 없기 때문이다.
 */
@Component
class LiveBroadcastStartupCleanup(
    private val service: LiveBroadcastService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun endLeftovers() {
        val ended = service.endAllUnfinished()
        if (ended > 0) log.info("지난 실행에서 남은 라이브 방송 {}개를 종료로 정리했습니다.", ended)
    }
}
