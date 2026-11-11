package com.chzzz.donation

import com.chzzz.common.BroadcastType
import com.chzzz.live.LiveBroadcastRepository
import com.chzzz.member.Member
import com.chzzz.recording.RecordingRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/** 후원이 가리키는 방송의 제목과 현재 소유자를 읽는다. 방송 모듈은 고치지 않고 저장소만 읽는다. */
@Component
class BroadcastDirectory(
    private val recordingRepository: RecordingRepository,
    private val liveBroadcastRepository: LiveBroadcastRepository,
) {
    fun find(type: BroadcastType, id: Long): BroadcastInfo? = when (type) {
        BroadcastType.RECORDING -> recordingRepository.findByIdOrNull(id)?.let { BroadcastInfo(it.title, it.owner) }
        BroadcastType.LIVE_BROADCAST -> liveBroadcastRepository.findByIdOrNull(id)?.let { BroadcastInfo(it.title, it.owner) }
    }
}

data class BroadcastInfo(val title: String, val owner: Member?)
