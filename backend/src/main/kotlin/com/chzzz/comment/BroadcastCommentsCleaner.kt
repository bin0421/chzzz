package com.chzzz.comment

import com.chzzz.common.BroadcastDeletedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 방송이 삭제되면 그 방송의 댓글을 지운다.
 * 트랜잭션 이벤트 리스너(커밋 뒤)가 아니라 `@EventListener`로 받아, 방송 삭제 트랜잭션 안에서 함께 지우고 함께 되돌려지게 한다.
 */
@Component
class BroadcastCommentsCleaner(
    private val commentRepository: CommentRepository,
) {
    @EventListener
    @Transactional
    fun onBroadcastDeleted(event: BroadcastDeletedEvent) {
        commentRepository.deleteAllOfBroadcast(event.type, event.broadcastId)
    }
}
