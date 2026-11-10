package com.chzzz.comment

import com.chzzz.common.FieldValidationException

/** 댓글 내용 규칙: 앞뒤 공백을 지운 뒤 1~500자. */
object CommentContent {
    const val MAX_LENGTH = 500

    fun normalize(content: String?): String {
        val trimmed = content?.trim().orEmpty()
        if (trimmed.isEmpty()) throw FieldValidationException("content", "댓글 내용을 입력해 주세요.")
        if (trimmed.length > MAX_LENGTH) {
            throw FieldValidationException("content", "댓글은 ${MAX_LENGTH}자 이하로 입력해 주세요.")
        }
        return trimmed
    }
}
