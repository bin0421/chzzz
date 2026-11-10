package com.chzzz.comment

import com.chzzz.common.BroadcastType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CommentRepository : JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = ["author"])
    fun findAllByBroadcastTypeAndBroadcastIdOrderByCreatedAtAscIdAsc(
        broadcastType: BroadcastType,
        broadcastId: Long,
    ): List<Comment>

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.broadcastType = :broadcastType AND c.broadcastId = :broadcastId")
    fun deleteAllOfBroadcast(broadcastType: BroadcastType, broadcastId: Long): Int
}
