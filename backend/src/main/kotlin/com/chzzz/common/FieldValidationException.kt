package com.chzzz.common

/** 사용자 입력이 규칙에 맞지 않을 때. 필드별 오류를 담아 400 Problem Details로 응답된다. */
class FieldValidationException(
    val errors: List<FieldErrorDetail>,
) : RuntimeException(errors.joinToString { "${it.field}: ${it.message}" }) {
    constructor(field: String, message: String) : this(listOf(FieldErrorDetail(field, message)))
}

data class FieldErrorDetail(
    val field: String,
    val message: String,
)
