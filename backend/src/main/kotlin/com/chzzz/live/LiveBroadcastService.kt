package com.chzzz.live

import com.chzzz.common.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.annotation.Value
import java.time.Clock
import java.time.Duration

@Service
class LiveBroadcastService(
    private val repository: LiveBroadcastRepository,
    private val clock: Clock,
    @Value("\${live.pending-timeout}") private val pendingTimeout: Duration,
) {
    /** 라이브 방송을 대기 상태로 만들고, 송출 키 원문은 결과로 한 번만 돌려준다. */
    @Transactional
    fun start(title: String?): StartedLiveBroadcast {
        val streamKey = StreamKey.generate()
        val broadcast = repository.save(LiveBroadcast.start(title, StreamKey.hash(streamKey), clock.instant()))
        return StartedLiveBroadcast(broadcast, streamKey)
    }

    /** 방송 목록용: 방송 중을 최근 시작순으로. 대기는 넣지 않는다. 시간 초과된 대기는 먼저 종료로 정리한다. */
    @Transactional
    fun list(): List<LiveBroadcast> {
        expireOverduePending()
        return repository.findAllByStatusOrderByStartedAtDescIdDesc(LiveBroadcastStatus.LIVE)
    }

    /** 대기 시간이 지난 라이브 방송을 모두 종료로 바꾼다. 주기 정리 작업도 이 규칙을 쓴다. */
    @Transactional
    fun expireOverduePending() {
        val now = clock.instant()
        repository.findAllByStatusAndCreatedAtBefore(LiveBroadcastStatus.PENDING, now.minus(pendingTimeout))
            .forEach { it.expireIfPendingTooLong(now, pendingTimeout) }
    }

    /** 단건 조회. 대기 시간이 지났으면 종료로 판정해 저장한 뒤 돌려준다. */
    @Transactional
    fun find(id: Long): LiveBroadcast =
        findCurrent(id) ?: throw NotFoundException("라이브 방송을 찾을 수 없습니다.")

    /** MediaMTX가 묻는 송출·시청 허가. 허용할 때만 true. 시간 초과된 대기는 먼저 종료로 판정한다. */
    @Transactional
    fun authorize(action: String?, pathName: String?, streamKey: String?): Boolean {
        val id = MediaPaths.idOf(pathName) ?: return false
        val broadcast = findCurrent(id) ?: return false
        return when (action) {
            "publish" -> broadcast.status == LiveBroadcastStatus.PENDING &&
                StreamKey.matches(streamKey, broadcast.streamKeyHash)
            "read" -> broadcast.status == LiveBroadcastStatus.LIVE
            else -> false
        }
    }

    /** MediaMTX "영상 준비됨" 훅: 대기 → 방송 중. 해당 없는 경로나 상태, 시간 초과된 대기는 무시한다. */
    @Transactional
    fun onStreamAvailable(pathName: String?) {
        val id = MediaPaths.idOf(pathName) ?: return
        findCurrent(id)?.goLive(clock.instant())
    }

    /** 지금 시각 기준의 상태로 조회한다: 대기 시간이 지났으면 종료로 바꿔 둔다. 트랜잭션 안에서 부른다. */
    private fun findCurrent(id: Long): LiveBroadcast? =
        repository.findByIdOrNull(id)?.also { it.expireIfPendingTooLong(clock.instant(), pendingTimeout) }
}

class StartedLiveBroadcast(val broadcast: LiveBroadcast, val streamKey: String)
