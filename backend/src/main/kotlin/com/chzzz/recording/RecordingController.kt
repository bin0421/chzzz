package com.chzzz.recording

import com.chzzz.common.NotFoundException
import com.chzzz.member.LoginMember
import com.chzzz.member.MemberResponse
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
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

    /** Range 요청(206/416)과 `Accept-Ranges`는 Spring MVC가 [Resource] 본문에 대해 처리한다. */
    @GetMapping("/{id}/video")
    fun video(@PathVariable id: Long): ResponseEntity<Resource> {
        val video = recordingService.findVideo(id) ?: throw NotFoundException("재생할 업로드 파일을 찾을 수 없습니다.")
        return ResponseEntity.ok().contentType(video.mediaType).body(video.resource)
    }

    @PostMapping
    fun register(
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) url: String?,
        @RequestParam(required = false) file: MultipartFile?,
        member: LoginMember,
    ): ResponseEntity<RecordingDetailResponse> {
        val upload = file?.takeIf { !it.isEmpty }?.let { UploadedFileInput(it.originalFilename, it.inputStream) }
        val recording = recordingService.register(title, url, upload, member)
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
    val owner: MemberResponse?,
) {
    companion object {
        fun from(recording: Recording) = RecordingDetailResponse(
            id = recording.id!!,
            title = recording.title,
            sourceType = recording.videoSource.type,
            videoUrl = when (recording.videoSource.type) {
                VideoSourceType.EXTERNAL_URL -> recording.videoSource.value
                VideoSourceType.UPLOADED_FILE -> "/api/recordings/${recording.id}/video"
            },
            createdAt = recording.createdAt,
            owner = recording.owner?.let(MemberResponse::from),
        )
    }
}

data class RecordingSummaryResponse(
    val id: Long,
    val title: String,
    val sourceType: VideoSourceType,
    val createdAt: Instant,
    val owner: MemberResponse?,
) {
    companion object {
        fun from(recording: Recording) = RecordingSummaryResponse(
            id = recording.id!!,
            title = recording.title,
            sourceType = recording.videoSource.type,
            createdAt = recording.createdAt,
            owner = recording.owner?.let(MemberResponse::from),
        )
    }
}
