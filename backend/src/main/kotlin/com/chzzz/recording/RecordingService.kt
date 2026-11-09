package com.chzzz.recording

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class RecordingService(
    private val recordingRepository: RecordingRepository,
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

    @Transactional(readOnly = true)
    fun list(): List<Recording> = recordingRepository.findAllByOrderByCreatedAtDescIdDesc()

    @Transactional(readOnly = true)
    fun find(id: Long): Recording? = recordingRepository.findByIdOrNull(id)
}
