package com.chzzz.member

import java.io.Serializable

/**
 * 세션에 담기는 로그인한 회원. 컨트롤러 파라미터로 받으면 로그인이 필요하다는 뜻이고(없으면 401),
 * `LoginMember?`로 받으면 로그인하지 않아도 된다.
 */
data class LoginMember(
    val id: Long,
    val nickname: String,
) : Serializable
