package com.chzzz.recording

import com.chzzz.common.FieldValidationException
import com.chzzz.recording.storage.UploadedFileStorage
import org.springframework.core.io.Resource
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.time.Instant
import java.util.UUID

@Service
class RecordingService(
    private val recordingRepository: RecordingRepository,
    private val uploadedFileStorage: UploadedFileStorage,
) {
    @Transactional
    fun register(title: String?, url: String): Recording =
        recordingRepository.save(
            Recording.register(
                title = title,
                videoSource = VideoSource.externalUrl(url),
                now = Instant.now(),
            )
        )

    /** 입력 검증 → 파일 저장 → 메타데이터 저장 순서로 등록한다. */
    fun registerUploadedFile(title: String?, upload: UploadedFileInput): Recording {
        val extension = upload.originalFilename.orEmpty().substringAfterLast('.', "").lowercase()
        if (extension !in ALLOWED_EXTENSIONS) {
            throw FieldValidationException("file", "mp4 또는 webm 파일만 올릴 수 있습니다.")
        }
        val storedName = "${UUID.randomUUID()}.$extension"
        val recording = Recording.register(
            title = title,
            videoSource = VideoSource.uploadedFile(storedName),
            now = Instant.now(),
        )
        uploadedFileStorage.save(storedName, upload.content)
        try {
            return recordingRepository.save(recording)
        } catch (e: RuntimeException) {
            uploadedFileStorage.delete(storedName)
            throw e
        }
    }

    private companion object {
        val ALLOWED_EXTENSIONS = setOf("mp4", "webm")
    }

    @Transactional(readOnly = true)
    fun list(): List<Recording> = recordingRepository.findAllByOrderByCreatedAtDescIdDesc()

    @Transactional(readOnly = true)
    fun find(id: Long): Recording? = recordingRepository.findByIdOrNull(id)

    /** 업로드 파일 녹화방송의 영상. 녹화방송이 없거나, 업로드 파일 출처가 아니거나, 파일이 디스크에 없으면 null. */
    @Transactional(readOnly = true)
    fun findVideo(id: Long): RecordingVideo? {
        val source = recordingRepository.findByIdOrNull(id)?.videoSource ?: return null
        if (source.type != VideoSourceType.UPLOADED_FILE) return null
        val storedName = source.value
        val resource = uploadedFileStorage.load(storedName) ?: return null
        val mediaType = MediaType.parseMediaType("video/" + storedName.substringAfterLast('.'))
        return RecordingVideo(resource, mediaType)
    }
}

class RecordingVideo(
    val resource: Resource,
    val mediaType: MediaType,
)

/** 등록할 때 사용자가 올린 파일. 웹 계층의 타입에 묶이지 않도록 이름과 내용만 받는다. */
class UploadedFileInput(
    val originalFilename: String?,
    val content: InputStream,
)
