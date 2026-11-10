package com.chzzz.live

import com.chzzz.member.TEST_PASSWORD
import com.chzzz.member.signUp
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

/** 아이디·닉네임이 겹치지 않는 회원으로 가입해 로그인된 세션을 돌려준다. 회원 테이블을 비우지 않는 테스트용. */
fun MockMvc.signUpSomeone(): MockHttpSession = signUp("u" + UUID.randomUUID().toString().replace("-", "").take(15))

/** 이미 가입한 회원으로 새 세션에서 로그인한다. 다른 탭·기기에서 접속한 것과 같다. */
fun MockMvc.loginAs(loginId: String, password: String = TEST_PASSWORD): MockHttpSession {
    val session = MockHttpSession()
    post("/api/auth/login") {
        this.session = session
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"loginId":"$loginId","password":"$password"}"""
    }.andExpect { status { isOk() } }
    return session
}

/** [session]의 회원으로 라이브 방송을 시작하고 식별자와 송출 키를 돌려준다. */
fun MockMvc.startLiveAs(session: MockHttpSession, title: String = "테스트 라이브"): LiveStarted {
    val body = post("/api/live-broadcasts") {
        this.session = session
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"title":"$title"}"""
    }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
    val id = Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    val key = Regex("\"streamKey\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    return LiveStarted(id, key)
}

data class LiveStarted(val id: Long, val streamKey: String)
