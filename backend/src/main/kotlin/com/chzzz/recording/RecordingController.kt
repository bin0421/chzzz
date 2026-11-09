package com.chzzz.recording

import com.chzzz.common.NotFoundException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

@RestController
@RequestMapping("/api/recordings")
class RecordingController(
    private val recordingService: RecordingService,
) {
    @GetMapping
    fun list(): List<RecordingSummaryResponse> =
        recordingService.list().map(RecordingSummaryResponse::from)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): RecordingDetailResponse {
        val recording = recordingService.find(id) ?: throw NotFoundException("녹화방송을 찾을 수 없습니다.")
        return RecordingDetailResponse.from(recording)
    }

    @PostMapping
    fun register(
        @RequestParam(required = false) title: String?,
        @RequestParam url: String,
    ): ResponseEntity<RecordingDetailResponse> {
        val recording = recordingService.register(title, url)
        return ResponseEntity
            .created(URI.create("/api/recordings/${recording.id}"))
            .body(RecordingDetailResponse.from(recording))
    }
}

data class RecordingDetailResponse(
    val id: Long,
    val title: String,
    val sourceType: VideoSourceType,
    val videoUrl: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(recording: Recording) = RecordingDetailResponse(
            id = recording.id!!,
            title = recording.title,
            sourceType = recording.videoSource.type,
            videoUrl = recording.videoSource.value,
            createdAt = recording.createdAt,
        )
    }
}

data class RecordingSummaryResponse(
    val id: Long,
    val title: String,
    val sourceType: VideoSourceType,
    val createdAt: Instant,
) {
    companion object {
        fun from(recording: Recording) = RecordingSummaryResponse(
            id = recording.id!!,
            title = recording.title,
            sourceType = recording.videoSource.type,
            createdAt = recording.createdAt,
        )
    }
}
