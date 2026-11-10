package com.chzzz.comment

import com.chzzz.common.BroadcastType
import com.chzzz.common.NotFoundException
import com.chzzz.common.Ownership
import com.chzzz.common.UnauthenticatedException
import com.chzzz.live.LiveBroadcastRepository
import com.chzzz.member.LoginMember
import com.chzzz.member.MemberRepository
import com.chzzz.recording.RecordingRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val memberRepository: MemberRepository,
    private val recordingRepository: RecordingRepository,
    private val liveBroadcastRepository: LiveBroadcastRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(broadcastType: BroadcastType, broadcastId: Long): List<Comment> {
        requireBroadcast(broadcastType, broadcastId)
        return commentRepository.findAllByBroadcastTypeAndBroadcastIdOrderByCreatedAtAscIdAsc(broadcastType, broadcastId)
    }

    @Transactional
    fun write(broadcastType: BroadcastType, broadcastId: Long, content: String?, member: LoginMember): Comment {
        requireBroadcast(broadcastType, broadcastId)
        val author = memberRepository.findByIdOrNull(member.id) ?: throw UnauthenticatedException()
        return commentRepository.save(Comment.write(broadcastType, broadcastId, author, content, clock.instant()))
    }

    /** 작성자만 고친다. 방송 소유자라도 남의 댓글은 고치지 못한다. */
    @Transactional
    fun edit(commentId: Long, content: String?, member: LoginMember): Comment {
        val comment = requireAuthoredBy(commentId, member)
        comment.edit(content, clock.instant())
        return comment
    }

    @Transactional
    fun delete(commentId: Long, member: LoginMember) {
        commentRepository.delete(requireAuthoredBy(commentId, member))
    }

    private fun requireAuthoredBy(commentId: Long, member: LoginMember): Comment {
        val comment = commentRepository.findByIdOrNull(commentId) ?: throw NotFoundException("댓글을 찾을 수 없습니다.")
        Ownership.requireOwner(comment.author.id, member, "댓글 작성자만 할 수 있습니다.")
        return comment
    }

    /** 방송 모듈의 저장소는 존재 확인으로만 읽는다. */
    private fun requireBroadcast(broadcastType: BroadcastType, broadcastId: Long) {
        val exists = when (broadcastType) {
            BroadcastType.RECORDING -> recordingRepository.existsById(broadcastId)
            BroadcastType.LIVE_BROADCAST -> liveBroadcastRepository.existsById(broadcastId)
        }
        if (!exists) throw NotFoundException("방송을 찾을 수 없습니다.")
    }
}
