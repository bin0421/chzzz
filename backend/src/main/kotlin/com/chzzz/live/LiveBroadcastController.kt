package com.chzzz.live

import com.chzzz.member.LoginMember
import com.chzzz.member.MemberResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

@RestController
@RequestMapping("/api/live-broadcasts")
class LiveBroadcastController(
    private val service: LiveBroadcastService,
) {
    @GetMapping
    fun list(): List<LiveBroadcastSummaryResponse> = service.list().map(LiveBroadcastSummaryResponse::from)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): LiveBroadcastDetailResponse =
        LiveBroadcastDetailResponse.from(service.find(id))

    /** 소유자 종료. 세션의 회원으로 검사하고 송출 키는 보지 않는다(ADR-0006). */
    @PostMapping("/{id}/end")
    fun end(@PathVariable id: Long, member: LoginMember): LiveBroadcastDetailResponse =
        LiveBroadcastDetailResponse.from(service.end(id, member))

    /** 소유자 제목 수정. */
    @PatchMapping("/{id}")
    fun rename(
        @PathVariable id: Long,
        @RequestBody request: RenameLiveBroadcastRequest,
        member: LoginMember,
    ): LiveBroadcastDetailResponse =
        LiveBroadcastDetailResponse.from(service.rename(id, request.title, member))

    /** 소유자 삭제. 종료된 방송만 지울 수 있다. */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long, member: LoginMember): ResponseEntity<Void> {
        service.delete(id, member)
        return ResponseEntity.noContent().build()
    }

    @PostMapping
    fun start(
        @RequestBody request: StartLiveBroadcastRequest,
        member: LoginMember,
    ): ResponseEntity<StartedLiveBroadcastResponse> {
        val started = service.start(request.title, member)
        val broadcast = started.broadcast
        return ResponseEntity
            .created(URI.create("/api/live-broadcasts/${broadcast.id}"))
            .body(
                StartedLiveBroadcastResponse(
                    id = broadcast.id!!,
                    title = broadcast.title,
                    status = broadcast.status,
                    streamKey = started.streamKey,
                    whipUrl = MediaPaths.whipUrl(broadcast.id!!),
                    createdAt = broadcast.createdAt,
                    owner = MemberResponse.from(member),
                )
            )
    }
}

data class StartLiveBroadcastRequest(val title: String?)

data class RenameLiveBroadcastRequest(val title: String?)

data class LiveBroadcastSummaryResponse(
    val id: Long,
    val title: String,
    val status: LiveBroadcastStatus,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val owner: MemberResponse?,
) {
    companion object {
        fun from(broadcast: LiveBroadcast) = LiveBroadcastSummaryResponse(
            id = broadcast.id!!,
            title = broadcast.title,
            status = broadcast.status,
            startedAt = broadcast.startedAt,
            endedAt = broadcast.endedAt,
            owner = broadcast.owner?.let(MemberResponse::from),
        )
    }
}

/** 시청자용 상세. 송출 키는 절대 담지 않는다. */
data class LiveBroadcastDetailResponse(
    val id: Long,
    val title: String,
    val status: LiveBroadcastStatus,
    val createdAt: Instant,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val whepUrl: String,
    val owner: MemberResponse?,
) {
    companion object {
        fun from(broadcast: LiveBroadcast) = LiveBroadcastDetailResponse(
            id = broadcast.id!!,
            title = broadcast.title,
            status = broadcast.status,
            createdAt = broadcast.createdAt,
            startedAt = broadcast.startedAt,
            endedAt = broadcast.endedAt,
            whepUrl = MediaPaths.whepUrl(broadcast.id!!),
            owner = broadcast.owner?.let(MemberResponse::from),
        )
    }
}

data class StartedLiveBroadcastResponse(
    val id: Long,
    val title: String,
    val status: LiveBroadcastStatus,
    val streamKey: String,
    val whipUrl: String,
    val createdAt: Instant,
    val owner: MemberResponse,
)
