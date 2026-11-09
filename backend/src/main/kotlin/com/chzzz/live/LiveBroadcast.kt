package com.chzzz.live

import com.chzzz.common.Title
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 라이브 방송: 송출자가 휴대폰 카메라·마이크로 지금 실시간으로 내보내는 방송. */
@Entity
@Table(name = "live_broadcast")
class LiveBroadcast private constructor(
    @Column(nullable = false, length = 100)
    val title: String,

    @Column(name = "stream_key_hash", nullable = false, length = 64)
    val streamKeyHash: String,

    @Column(nullable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: LiveBroadcastStatus = LiveBroadcastStatus.PENDING
        protected set

    var startedAt: Instant? = null
        protected set

    var endedAt: Instant? = null
        protected set

    /** 영상이 들어옴: 대기일 때만 방송 중이 된다. 바뀌었으면 true. */
    fun goLive(now: Instant): Boolean {
        if (status != LiveBroadcastStatus.PENDING) return false
        status = LiveBroadcastStatus.LIVE
        startedAt = now.truncatedTo(ChronoUnit.MICROS)
        return true
    }

    /** 송출자 종료·끊김: 대기·방송 중일 때만 종료된다. 바뀌었으면 true. */
    fun end(now: Instant): Boolean {
        if (status == LiveBroadcastStatus.ENDED) return false
        status = LiveBroadcastStatus.ENDED
        endedAt = now.truncatedTo(ChronoUnit.MICROS)
        return true
    }

    companion object {
        fun start(title: String?, streamKeyHash: String, now: Instant): LiveBroadcast =
            LiveBroadcast(Title.normalize(title), streamKeyHash, now.truncatedTo(ChronoUnit.MICROS))
    }
}

enum class LiveBroadcastStatus { PENDING, LIVE, ENDED }
