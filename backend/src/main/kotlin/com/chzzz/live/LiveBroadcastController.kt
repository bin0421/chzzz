package com.chzzz.live

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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

    /** 송출자 종료. 송출 키는 `Authorization: Bearer <송출 키>`로 받는다. */
    @PostMapping("/{id}/end")
    fun end(
        @PathVariable id: Long,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): LiveBroadcastDetailResponse =
        LiveBroadcastDetailResponse.from(service.end(id, bearerToken(authorization)))

    private fun bearerToken(authorization: String?): String? =
        authorization?.takeIf { it.startsWith(BEARER_PREFIX) }?.removePrefix(BEARER_PREFIX)?.trim()

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }

    @PostMapping
    fun start(@RequestBody request: StartLiveBroadcastRequest): ResponseEntity<StartedLiveBroadcastResponse> {
        val started = service.start(request.title)
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
                )
            )
    }
}

data class StartLiveBroadcastRequest(val title: String?)

data class LiveBroadcastSummaryResponse(
    val id: Long,
    val title: String,
    val status: LiveBroadcastStatus,
    val startedAt: Instant?,
    val endedAt: Instant?,
) {
    companion object {
        fun from(broadcast: LiveBroadcast) = LiveBroadcastSummaryResponse(
            id = broadcast.id!!,
            title = broadcast.title,
            status = broadcast.status,
            startedAt = broadcast.startedAt,
            endedAt = broadcast.endedAt,
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
)
