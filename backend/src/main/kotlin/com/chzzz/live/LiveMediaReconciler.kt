package com.chzzz.live

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 주기적으로 DB의 방송 중과 MediaMTX의 실제 송출을 대조한다(ADR-0010).
 * 인스턴스마다 돌아도 같은 결과다: 이미 종료된 방송은 다시 종료하지 않는다.
 * 대기 상태는 대기 시간 초과 규칙([LivePendingSweeper])이 정리한다.
 */
@Component
class LiveMediaReconciler(
    private val service: LiveBroadcastService,
    private val mediaServer: MediaServer,
    properties: MediaMtxProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val enabled = properties.apiUrl.isNotBlank()

    init {
        if (!enabled) log.info("MEDIAMTX_API_URL이 비어 있어 라이브 방송 송출 대조를 하지 않습니다.")
    }

    @Scheduled(fixedDelayString = "\${live.reconcile-interval}", initialDelayString = "\${live.reconcile-interval}")
    fun reconcile() {
        if (!enabled) return
        when (val paths = mediaServer.publishingPaths()) {
            is PublishingPaths.Known -> {
                val ended = service.endLiveWithoutPublisher(paths.names)
                if (ended > 0) log.info("MediaMTX에 송출이 없는 라이브 방송 {}개를 종료로 정리했습니다.", ended)
            }
            // 송출 여부를 모르면 끊긴 방송으로 볼 수 없다. 다음 주기에 다시 묻는다.
            is PublishingPaths.Unknown -> log.warn("MediaMTX 제어 API에 묻지 못해 송출 대조를 건너뜁니다: {}", paths.reason)
        }
    }
}
