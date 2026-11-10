package com.chzzz.recording

import com.chzzz.common.Title
import com.chzzz.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 녹화방송: 제목과 영상 출처 하나를 가진, 다시 볼 수 있도록 등록된 방송 영상. */
@Entity
@Table(name = "recording")
class Recording private constructor(
    title: String,

    @Embedded
    val videoSource: VideoSource,

    @Column(nullable = false)
    val createdAt: Instant,

    /** 소유자. 회원 기능 이전에 등록된 녹화방송은 null이다. */
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

    /** 제목을 바꾼다. 규칙은 등록과 같다. */
    fun rename(newTitle: String?) {
        title = Title.normalize(newTitle)
    }

    companion object {
        fun register(title: String?, videoSource: VideoSource, owner: Member, now: Instant): Recording =
            // PostgreSQL timestamptz는 마이크로초까지 저장한다. 응답과 저장값이 어긋나지 않도록 맞춘다.
            Recording(Title.normalize(title), videoSource, now.truncatedTo(ChronoUnit.MICROS), owner)
    }
}
