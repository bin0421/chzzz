package com.chzzz.common

/** 방송 종류. 댓글처럼 녹화방송과 라이브 방송 모두에 달리는 것이 어느 방송을 가리키는지 나타낸다. */
enum class BroadcastType { RECORDING, LIVE_BROADCAST }

/**
 * 방송이 삭제되었다는 알림. 녹화방송·라이브 방송 삭제가 발행하고, 댓글 모듈이 받아 그 방송의 댓글을 지운다.
 * 방송 모듈이 댓글 모듈을 직접 알지 않게 하려고 이벤트로 잇는다.
 */
data class BroadcastDeletedEvent(
    val type: BroadcastType,
    val broadcastId: Long,
)
