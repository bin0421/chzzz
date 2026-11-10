package com.chzzz.comment

import com.chzzz.common.BroadcastType
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
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 댓글: 로그인한 회원이 녹화방송이나 라이브 방송에 남긴 글. 작성자만 수정·삭제한다. */
@Entity
@Table(name = "comment")
class Comment private constructor(
    @Enumerated(EnumType.STRING)
    @Column(name = "broadcast_type", nullable = false, length = 20)
    val broadcastType: BroadcastType,

    @Column(name = "broadcast_id", nullable = false)
    val broadcastId: Long,

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    val author: Member,

    content: String,

    @Column(nullable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, length = CommentContent.MAX_LENGTH)
    var content: String = content
        protected set

    /** 수정 전에는 null. 화면은 이 값으로 "수정됨"을 표시한다. */
    var updatedAt: Instant? = null
        protected set

    fun edit(content: String?, now: Instant) {
        this.content = CommentContent.normalize(content)
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS)
    }

    companion object {
        fun write(broadcastType: BroadcastType, broadcastId: Long, author: Member, content: String?, now: Instant): Comment =
            // PostgreSQL timestamptz는 마이크로초까지 저장한다. 응답과 저장값이 어긋나지 않도록 맞춘다.
            Comment(broadcastType, broadcastId, author, CommentContent.normalize(content), now.truncatedTo(ChronoUnit.MICROS))
    }
}
