package com.chzzz.comment

import com.chzzz.common.BroadcastType
import com.chzzz.member.LoginMember
import com.chzzz.member.MemberResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class CommentController(
    private val commentService: CommentService,
) {
    @GetMapping("/api/recordings/{id}/comments")
    fun listForRecording(@PathVariable id: Long): List<CommentResponse> =
        commentService.list(BroadcastType.RECORDING, id).map(CommentResponse::from)

    @PostMapping("/api/recordings/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    fun writeOnRecording(@PathVariable id: Long, @RequestBody body: CommentRequest, member: LoginMember): CommentResponse =
        CommentResponse.from(commentService.write(BroadcastType.RECORDING, id, body.content, member))

    @GetMapping("/api/live-broadcasts/{id}/comments")
    fun listForLiveBroadcast(@PathVariable id: Long): List<CommentResponse> =
        commentService.list(BroadcastType.LIVE_BROADCAST, id).map(CommentResponse::from)

    @PostMapping("/api/live-broadcasts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    fun writeOnLiveBroadcast(@PathVariable id: Long, @RequestBody body: CommentRequest, member: LoginMember): CommentResponse =
        CommentResponse.from(commentService.write(BroadcastType.LIVE_BROADCAST, id, body.content, member))

    @PatchMapping("/api/comments/{id}")
    fun edit(@PathVariable id: Long, @RequestBody body: CommentRequest, member: LoginMember): CommentResponse =
        CommentResponse.from(commentService.edit(id, body.content, member))

    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long, member: LoginMember) {
        commentService.delete(id, member)
    }
}

data class CommentRequest(
    val content: String? = null,
)

data class CommentResponse(
    val id: Long,
    val content: String,
    val author: MemberResponse,
    val createdAt: Instant,
    val updatedAt: Instant?,
) {
    companion object {
        fun from(comment: Comment) = CommentResponse(
            id = comment.id!!,
            content = comment.content,
            author = MemberResponse(comment.author.id!!, comment.author.nickname),
            createdAt = comment.createdAt,
            updatedAt = comment.updatedAt,
        )
    }
}
