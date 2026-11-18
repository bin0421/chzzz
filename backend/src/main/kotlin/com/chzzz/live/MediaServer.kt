package com.chzzz.live

/**
 * 미디어 서버(MediaMTX) 경계: 지금 실제로 송출이 들어와 있는 경로를 묻는다.
 * 실제 구현은 MediaMTX 제어 API를 부르고, 테스트는 가짜 구현으로 바꿔 끼운다.
 */
interface MediaServer {
    fun publishingPaths(): PublishingPaths
}

sealed interface PublishingPaths {
    /** 미디어 서버가 알려 준 송출 중인 경로 이름(`live/{id}`) 전부. */
    data class Known(val names: Set<String>) : PublishingPaths

    /** 미디어 서버에 묻지 못했다(연결 실패·오류 응답). 송출 여부를 모른다. */
    data class Unknown(val reason: String) : PublishingPaths
}
