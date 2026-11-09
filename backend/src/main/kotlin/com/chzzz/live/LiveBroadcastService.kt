package com.chzzz.live

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

    /** 방송 목록용: 방송 중을 최근 시작순으로. 대기는 넣지 않는다. */
    @Transactional(readOnly = true)
    fun list(): List<LiveBroadcast> =
        repository.findAllByStatusOrderByStartedAtDescIdDesc(LiveBroadcastStatus.LIVE)

    @Transactional(readOnly = true)
    fun find(id: Long): LiveBroadcast =
        repository.findByIdOrNull(id) ?: throw NotFoundException("라이브 방송을 찾을 수 없습니다.")

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
}

class StartedLiveBroadcast(val broadcast: LiveBroadcast, val streamKey: String)
