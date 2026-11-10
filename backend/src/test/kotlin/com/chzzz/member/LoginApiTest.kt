package com.chzzz.member

import com.chzzz.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class LoginApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        mockMvc.signUp("streamer", "스트리머")
    }

    @Test
    fun `맞는 아이디와 비밀번호로 로그인하면 200과 회원 정보를 주고 로그인 상태가 된다`() {
        val session = MockHttpSession()

        login("streamer", TEST_PASSWORD, session).andExpect {
            status { isOk() }
            jsonPath("$.id") { isNumber() }
            jsonPath("$.nickname") { value("스트리머") }
            jsonPath("$.loginId") { doesNotExist() }
        }

        mockMvc.get("/api/auth/me") { this.session = session }.andExpect {
            status { isOk() }
            jsonPath("$.nickname") { value("스트리머") }
        }
    }

    @Test
    fun `비밀번호가 틀리거나 없는 아이디면 같은 401 메시지를 주고 로그인되지 않는다`() {
        val session = MockHttpSession()
        val message = "아이디 또는 비밀번호가 올바르지 않습니다."

        login("streamer", "wrong-password", session).andExpect {
            status { isUnauthorized() }
            content { contentType("application/problem+json") }
            jsonPath("$.detail") { value(message) }
        }
        login("nobody", TEST_PASSWORD, session).andExpect {
            status { isUnauthorized() }
            jsonPath("$.detail") { value(message) }
        }

        mockMvc.get("/api/auth/me") { this.session = session }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `로그인하면 세션 ID가 바뀐다`() {
        val session = MockHttpSession()
        val before = session.id

        login("streamer", TEST_PASSWORD, session).andExpect { status { isOk() } }

        assertThat(session.id).isNotEqualTo(before)
    }

    private fun login(loginId: String, password: String, session: MockHttpSession): ResultActionsDsl =
        mockMvc.post("/api/auth/login") {
            this.session = session
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"loginId":"$loginId","password":"$password"}"""
        }
}
