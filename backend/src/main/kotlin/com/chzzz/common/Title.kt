package com.chzzz.common

/** 제목 규칙: 녹화방송과 라이브 방송이 함께 쓴다. 앞뒤 공백을 지운 뒤 1~100자. */
object Title {
    const val MAX_LENGTH = 100

    fun normalize(title: String?): String {
        val trimmed = title?.trim().orEmpty()
        if (trimmed.isEmpty()) throw FieldValidationException("title", "제목을 입력해 주세요.")
        if (trimmed.length > MAX_LENGTH) {
            throw FieldValidationException("title", "제목은 ${MAX_LENGTH}자 이하로 입력해 주세요.")
        }
        return trimmed
    }
}
