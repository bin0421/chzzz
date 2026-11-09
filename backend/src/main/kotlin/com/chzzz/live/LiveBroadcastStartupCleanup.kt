package com.chzzz.live

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.stereotype.Component

/**
 * 서비스가 시작될 때 대기·방송 중으로 남은 라이브 방송을 종료로 정리한다.
 * 재시작하면 MediaMTX의 연결도 끊겨 있어서, 남은 방송은 다시 이어질 수 없기 때문이다.
 *
 * 웹 서버가 요청을 받기 전에 정리하려고 `ApplicationReadyEvent`가 아니라 빈 초기화 직후에 실행한다
 * (`ApplicationReadyEvent`는 Tomcat이 열린 뒤라, 잠깐 동안 남은 방송이 그대로 보였다).
 */
@Component
class LiveBroadcastStartupCleanup(
    private val service: LiveBroadcastService,
) : SmartInitializingSingleton {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterSingletonsInstantiated() {
        val ended = service.endAllUnfinished()
        if (ended > 0) log.info("지난 실행에서 남은 라이브 방송 {}개를 종료로 정리했습니다.", ended)
    }
}
