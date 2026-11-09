package com.chzzz.recording

import com.chzzz.common.FieldValidationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

@Service
class RecordingService(
    private val recordingRepository: RecordingRepository,
) {
    @Transactional
    fun register(title: String?, url: String?, file: MultipartFile?): Recording {
        val externalUrl = url?.takeIf { it.isNotBlank() }
        val uploadedFile = file?.takeIf { !it.isEmpty }
        VideoSource.requireExactlyOne(hasFile = uploadedFile != null, hasUrl = externalUrl != null)
        // 업로드 파일 출처는 티켓 03에서 구현한다.
        val videoSource = externalUrl?.let(VideoSource::externalUrl)
            ?: throw FieldValidationException("file", "업로드 파일 등록은 아직 지원하지 않습니다.")
        return recordingRepository.save(
            Recording.register(
                title = title,
                videoSource = videoSource,
                now = Instant.now(),
            )
        )
    }

    @Transactional(readOnly = true)
    fun list(): List<Recording> = recordingRepository.findAllByOrderByCreatedAtDescIdDesc()

    @Transactional(readOnly = true)
    fun find(id: Long): Recording? = recordingRepository.findByIdOrNull(id)
}
