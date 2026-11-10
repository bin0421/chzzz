package com.chzzz.member

import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

const val TEST_PASSWORD = "password123"

/** 회원가입 API를 실제로 불러 로그인된 세션을 돌려준다. 이후 요청에 `session = ...`으로 실어 보낸다. */
fun MockMvc.signUp(loginId: String, nickname: String = loginId, password: String = TEST_PASSWORD): MockHttpSession {
    val session = MockHttpSession()
    post("/api/auth/signup") {
        this.session = session
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"loginId":"$loginId","password":"$password","nickname":"$nickname"}"""
    }.andExpect { status { isCreated() } }
    return session
}
