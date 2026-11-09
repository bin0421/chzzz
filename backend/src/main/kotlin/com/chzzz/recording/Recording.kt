package com.chzzz.recording

import com.chzzz.common.FieldValidationException
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 녹화방송: 제목과 영상 출처 하나를 가진, 다시 볼 수 있도록 등록된 방송 영상. */
@Entity
@Table(name = "recording")
class Recording private constructor(
    @Column(nullable = false, length = 100)
    val title: String,

    @Embedded
    val videoSource: VideoSource,

    @Column(nullable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    companion object {
        const val TITLE_MAX_LENGTH = 100

        fun register(title: String?, videoSource: VideoSource, now: Instant): Recording {
            val trimmed = title?.trim().orEmpty()
            if (trimmed.isEmpty()) throw FieldValidationException("title", "제목을 입력해 주세요.")
            if (trimmed.length > TITLE_MAX_LENGTH) {
                throw FieldValidationException("title", "제목은 ${TITLE_MAX_LENGTH}자 이하로 입력해 주세요.")
            }
            // PostgreSQL timestamptz는 마이크로초까지 저장한다. 응답과 저장값이 어긋나지 않도록 맞춘다.
            return Recording(trimmed, videoSource, now.truncatedTo(ChronoUnit.MICROS))
        }
    }
}
