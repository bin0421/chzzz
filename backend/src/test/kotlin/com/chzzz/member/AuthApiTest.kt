package com.chzzz.member

import com.chzzz.TestcontainersConfiguration
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
    }

    @Test
    fun `회원가입하면 201과 회원 식별자·닉네임을 주고 바로 로그인된다`() {
        val session = MockHttpSession()

        mockMvc.post("/api/auth/signup") {
            this.session = session
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"loginId":"streamer_1","password":"$TEST_PASSWORD","nickname":"스트리머"}"""
        }.andExpect {
            status { isCreated() }
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
    fun `로그인하지 않았으면 me는 401 Problem Details다`() {
        mockMvc.get("/api/auth/me").andExpect {
            status { isUnauthorized() }
            content { contentType("application/problem+json") }
        }
    }

    @Test
    fun `로그아웃하면 204이고 이후 me는 401이다`() {
        val session = mockMvc.signUp("leaving")

        mockMvc.post("/api/auth/logout") {
            this.session = session
            with(csrf())
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/auth/me") { this.session = session }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `로그인하지 않고 로그아웃하면 401이다`() {
        mockMvc.post("/api/auth/logout") { with(csrf()) }.andExpect { status { isUnauthorized() } }
    }
}
