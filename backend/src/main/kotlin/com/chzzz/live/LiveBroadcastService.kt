package com.chzzz.live

import com.chzzz.common.ConflictException
import com.chzzz.common.ForbiddenException
import com.chzzz.common.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class LiveBroadcastService(
    private val repository: LiveBroadcastRepository,
    private val clock: Clock,
) {
    /** 라이브 방송을 대기 상태로 만들고, 송출 키 원문은 결과로 한 번만 돌려준다. */
    @Transactional
    fun start(title: String?): StartedLiveBroadcast {
        val streamKey = StreamKey.generate()
        val broadcast = repository.save(LiveBroadcast.start(title, StreamKey.hash(streamKey), clock.instant()))
        return StartedLiveBroadcast(broadcast, streamKey)
    }

    /** 방송 목록용: 방송 중 전부를 최근 시작순으로, 이어서 종료 중 최근 10개를 최근 종료순으로. 대기는 넣지 않는다. */
    @Transactional(readOnly = true)
    fun list(): List<LiveBroadcast> =
        repository.findAllByStatusOrderByStartedAtDescIdDesc(LiveBroadcastStatus.LIVE) +
            repository.findTop10ByStatusOrderByEndedAtDescIdDesc(LiveBroadcastStatus.ENDED)

    @Transactional(readOnly = true)
    fun find(id: Long): LiveBroadcast =
        repository.findByIdOrNull(id) ?: throw NotFoundException("라이브 방송을 찾을 수 없습니다.")

    /** 송출자 종료: 송출 키가 맞아야 하고, 이미 종료된 방송은 다시 종료할 수 없다. */
    @Transactional
    fun end(id: Long, streamKey: String?): LiveBroadcast {
        val broadcast = find(id)
        if (!StreamKey.matches(streamKey, broadcast.streamKeyHash)) {
            throw ForbiddenException("송출 키가 없거나 맞지 않습니다.")
        }
        if (!broadcast.end(clock.instant())) {
            throw ConflictException("이미 종료된 라이브 방송입니다.")
        }
        return broadcast
    }

    /** 대기·방송 중인 라이브 방송을 모두 종료한다(재시작 정리). 종료한 개수를 돌려준다. */
    @Transactional
    fun endAllUnfinished(): Int {
        val now = clock.instant()
        return repository.findAllByStatusIn(listOf(LiveBroadcastStatus.PENDING, LiveBroadcastStatus.LIVE))
            .count { it.end(now) }
    }

    /** MediaMTX가 묻는 송출·시청 허가. 허용할 때만 true. */
    @Transactional(readOnly = true)
    fun authorize(action: String?, pathName: String?, streamKey: String?): Boolean {
        val id = MediaPaths.idOf(pathName) ?: return false
        val broadcast = repository.findByIdOrNull(id) ?: return false
        return when (action) {
            "publish" -> broadcast.status == LiveBroadcastStatus.PENDING &&
                StreamKey.matches(streamKey, broadcast.streamKeyHash)
            "read" -> broadcast.status == LiveBroadcastStatus.LIVE
            else -> false
        }
    }

    /** MediaMTX "영상 준비됨" 훅: 대기 → 방송 중. 해당 없는 경로나 상태는 무시한다. */
    @Transactional
    fun onStreamAvailable(pathName: String?) {
        val id = MediaPaths.idOf(pathName) ?: return
        repository.findByIdOrNull(id)?.goLive(clock.instant())
    }

    /** MediaMTX "영상 끊김" 훅: 대기·방송 중 → 종료. 이미 종료됐거나 해당 없는 경로는 무시한다. */
    @Transactional
    fun onStreamUnavailable(pathName: String?) {
        val id = MediaPaths.idOf(pathName) ?: return
        repository.findByIdOrNull(id)?.end(clock.instant())
    }
}

class StartedLiveBroadcast(val broadcast: LiveBroadcast, val streamKey: String)
