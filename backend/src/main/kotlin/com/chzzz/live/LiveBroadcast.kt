package com.chzzz.live

import com.chzzz.common.Title
import com.chzzz.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 라이브 방송: 송출자가 휴대폰 카메라·마이크로 지금 실시간으로 내보내는 방송. */
@Entity
@Table(name = "live_broadcast")
class LiveBroadcast private constructor(
    title: String,

    @Column(name = "stream_key_hash", nullable = false, length = 64)
    val streamKeyHash: String,

    @Column(nullable = false)
    val createdAt: Instant,

    /** 소유자: 시작한 회원. 회원 기능 이전에 시작된 라이브 방송은 null이다. */
    @ManyToOne
    @JoinColumn(name = "owner_id")
    val owner: Member?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, length = 100)
    var title: String = title
        protected set

    /** 제목 수정: 대기·방송 중·종료 어느 상태에서나 할 수 있다. */
    fun rename(newTitle: String?) {
        title = Title.normalize(newTitle)
    }

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

    /**
     * 대기 시간 초과: 대기가 [timeout]을 넘었으면 종료로 바꾼다. 바뀌었으면 true.
     * 종료 시각은 언제 판정했든 같도록 시간 초과가 된 시각(생성 시각 + [timeout])으로 남긴다.
     */
    fun expireIfPendingTooLong(now: Instant, timeout: Duration): Boolean {
        val deadline = createdAt.plus(timeout)
        if (status != LiveBroadcastStatus.PENDING || !now.isAfter(deadline)) return false
        status = LiveBroadcastStatus.ENDED
        endedAt = deadline.truncatedTo(ChronoUnit.MICROS)
        return true
    }

    companion object {
        fun start(title: String?, streamKeyHash: String, owner: Member, now: Instant): LiveBroadcast =
            LiveBroadcast(Title.normalize(title), streamKeyHash, now.truncatedTo(ChronoUnit.MICROS), owner)
    }
}

enum class LiveBroadcastStatus { PENDING, LIVE, ENDED }
