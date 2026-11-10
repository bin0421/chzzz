package com.chzzz.member

import com.chzzz.common.FieldErrorDetail

/** 회원가입 입력 규칙. 위반한 필드마다 오류를 돌려준다. */
object MemberRules {
    private val LOGIN_ID_PATTERN = Regex("^[a-z0-9_]{4,20}$")
    private val PASSWORD_LENGTH = 8..64

    fun normalizeLoginId(loginId: String?): String = loginId.orEmpty().trim()

    fun validate(loginId: String, password: String?, nickname: String): List<FieldErrorDetail> = buildList {
        when {
            loginId.isEmpty() -> add(FieldErrorDetail("loginId", "아이디를 입력해 주세요."))
            !LOGIN_ID_PATTERN.matches(loginId) ->
                add(FieldErrorDetail("loginId", "아이디는 영문 소문자, 숫자, _로 4~20자여야 합니다."))
        }
        when {
            password.isNullOrEmpty() -> add(FieldErrorDetail("password", "비밀번호를 입력해 주세요."))
            password.length !in PASSWORD_LENGTH -> add(FieldErrorDetail("password", "비밀번호는 8~64자여야 합니다."))
        }
        Nickname.error(nickname)?.let { add(FieldErrorDetail("nickname", it)) }
    }
}

/** 닉네임: 앞뒤 공백을 지운 1~20자. 중복은 대소문자를 무시하고 판정한다. */
object Nickname {
    private const val MAX_LENGTH = 20

    fun normalize(nickname: String?): String = nickname.orEmpty().trim()

    fun keyOf(normalized: String): String = normalized.lowercase()

    fun error(normalized: String): String? = when {
        normalized.isEmpty() -> "닉네임을 입력해 주세요."
        normalized.length > MAX_LENGTH -> "닉네임은 20자 이하로 입력해 주세요."
        else -> null
    }
}
