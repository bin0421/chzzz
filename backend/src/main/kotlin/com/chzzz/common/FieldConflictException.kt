package com.chzzz.common

/** 입력값이 이미 쓰이고 있어 받아들일 수 없을 때(아이디·닉네임 중복 등). 필드별 오류를 담아 409 Problem Details로 응답된다. */
class FieldConflictException(
    val errors: List<FieldErrorDetail>,
) : RuntimeException(errors.joinToString { "${it.field}: ${it.message}" })
