package com.chzzz.recording

import com.chzzz.common.FieldValidationException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.net.URI

/** 영상 출처: 녹화방송의 영상을 어디서 가져와 재생하는지. 종류 + 값. */
@Embeddable
class VideoSource(
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    val type: VideoSourceType,

    @Column(name = "source_value", nullable = false)
    val value: String,
) {
    companion object {
        /** 녹화방송의 영상 출처는 업로드 파일 또는 외부 URL 중 정확히 하나다. */
        fun requireExactlyOne(hasFile: Boolean, hasUrl: Boolean) {
            if (!hasFile && !hasUrl) {
                throw FieldValidationException("source", "업로드 파일이나 외부 URL 중 하나를 입력해 주세요.")
            }
            if (hasFile && hasUrl) {
                throw FieldValidationException("source", "업로드 파일과 외부 URL 중 하나만 입력해 주세요.")
            }
        }

        /** 형식만 검사하고 그 주소에 접속하지 않는다(ADR-0002). */
        fun externalUrl(url: String): VideoSource {
            val trimmed = url.trim()
            val uri = runCatching { URI(trimmed) }.getOrNull()
            if (uri?.scheme?.lowercase() !in ALLOWED_URL_SCHEMES) {
                throw FieldValidationException("url", "http 또는 https로 시작하는 영상 주소를 입력해 주세요.")
            }
            if (uri?.host.isNullOrBlank()) {
                throw FieldValidationException("url", "영상 주소에 호스트가 없습니다. 주소를 확인해 주세요.")
            }
            return VideoSource(VideoSourceType.EXTERNAL_URL, trimmed)
        }

        private val ALLOWED_URL_SCHEMES = setOf("http", "https")
    }
}

enum class VideoSourceType {
    EXTERNAL_URL,
}
