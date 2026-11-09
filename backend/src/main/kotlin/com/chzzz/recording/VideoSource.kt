package com.chzzz.recording

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

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
        fun externalUrl(url: String) = VideoSource(VideoSourceType.EXTERNAL_URL, url)

        /** [storedName]은 서비스가 정한 저장 이름(UUID + 확장자)이다. */
        fun uploadedFile(storedName: String) = VideoSource(VideoSourceType.UPLOADED_FILE, storedName)
    }
}

enum class VideoSourceType {
    EXTERNAL_URL,
    UPLOADED_FILE,
}
