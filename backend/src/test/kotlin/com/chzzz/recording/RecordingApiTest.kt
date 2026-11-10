package com.chzzz.recording

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import com.chzzz.TestcontainersConfiguration
import com.chzzz.member.signUp
import org.springframework.mock.web.MockHttpSession
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class RecordingApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    /** 녹화방송 등록은 로그인이 필요하다. */
    lateinit var member: MockHttpSession

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording RESTART IDENTITY")
        member = mockMvc.signUp("recorder", "녹화방송회원")
    }

    @Test
    fun `외부 URL로 녹화방송을 등록하면 201과 상세를 돌려준다`() {
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            param("title", "첫 방송 다시보기")
            param("url", "https://cdn.example.com/videos/first.mp4")
        }.andExpect {
            status { isCreated() }
            header { string("Location", matchesPattern("/api/recordings/\\d+")) }
            jsonPath("$.id") { isNumber() }
            jsonPath("$.title") { value("첫 방송 다시보기") }
            jsonPath("$.sourceType") { value("EXTERNAL_URL") }
            jsonPath("$.videoUrl") { value("https://cdn.example.com/videos/first.mp4") }
            jsonPath("$.createdAt") { isString() }
        }
    }

    @Test
    fun `녹화방송 목록은 최신 등록순이다`() {
        register("먼저 등록한 방송", "https://cdn.example.com/a.mp4")
        register("나중에 등록한 방송", "https://cdn.example.com/b.mp4")

        mockMvc.get("/api/recordings").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].title") { value("나중에 등록한 방송") }
            jsonPath("$[0].sourceType") { value("EXTERNAL_URL") }
            jsonPath("$[0].id") { isNumber() }
            jsonPath("$[0].createdAt") { isString() }
            jsonPath("$[1].title") { value("먼저 등록한 방송") }
        }
    }

    @Test
    fun `등록한 녹화방송의 상세를 조회한다`() {
        val location = register("상세 볼 방송", "https://cdn.example.com/detail.mp4")

        mockMvc.get(location).andExpect {
            status { isOk() }
            jsonPath("$.title") { value("상세 볼 방송") }
            jsonPath("$.sourceType") { value("EXTERNAL_URL") }
            jsonPath("$.videoUrl") { value("https://cdn.example.com/detail.mp4") }
            jsonPath("$.createdAt") { isString() }
        }
    }

    @Test
    fun `등록 응답의 등록일시는 이후 조회한 등록일시와 같다`() {
        val created = mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            param("title", "시각 비교 방송")
            param("url", "https://cdn.example.com/time.mp4")
        }.andReturn().response
        val createdAt = Regex("\"createdAt\":\"([^\"]+)\"").find(created.contentAsString)!!.groupValues[1]

        mockMvc.get(created.getHeader("Location")!!).andExpect {
            jsonPath("$.createdAt") { value(createdAt) }
        }
    }

    @Test
    fun `제목이 비어 있으면 400 Problem Details로 title 오류를 돌려준다`() {
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            param("title", "   ")
            param("url", "https://cdn.example.com/no-title.mp4")
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.status") { value(400) }
            jsonPath("$.errors[0].field") { value("title") }
            jsonPath("$.errors[0].message") { value("제목을 입력해 주세요.") }
        }

        mockMvc.get("/api/recordings").andExpect {
            jsonPath("$.length()") { value(0) }
        }
    }

    private fun register(title: String, url: String): String =
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            param("title", title)
            param("url", url)
        }.andExpect { status { isCreated() } }
            .andReturn().response.getHeader("Location")!!
}
