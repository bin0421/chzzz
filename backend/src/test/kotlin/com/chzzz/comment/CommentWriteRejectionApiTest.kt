package com.chzzz.comment

import com.chzzz.TestcontainersConfiguration
import com.chzzz.member.signUp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** 댓글 작성 거절: 비로그인 401 → 방송 없음 404 → 내용 규칙 400. 거절 뒤 목록은 비어 있다. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class CommentWriteRejectionApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private var recordingId: Long = 0

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording RESTART IDENTITY")
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
        recordingId = jdbcTemplate.queryForObject(
            "INSERT INTO recording (title, source_type, source_value, created_at) " +
                "VALUES ('녹화', 'EXTERNAL_URL', 'https://cdn.example.com/a.mp4', now()) RETURNING id",
            Long::class.java,
        )!!
    }

    @Test
    fun `로그인하지 않고 댓글을 쓰면 401이고 목록에 나오지 않는다`() {
        mockMvc.post("/api/recordings/$recordingId/comments") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"비로그인 댓글"}"""
        }.andExpect {
            status { isUnauthorized() }
            content { contentType("application/problem+json") }
        }

        expectNoComments()
    }

    @Test
    fun `없는 방송의 댓글 목록과 작성은 404이다`() {
        val member = mockMvc.signUp("viewer")
        mockMvc.get("/api/recordings/999/comments").andExpect { status { isNotFound() } }
        mockMvc.get("/api/live-broadcasts/999/comments").andExpect { status { isNotFound() } }
        mockMvc.post("/api/live-broadcasts/999/comments") {
            session = member
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"어디에"}"""
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `비로그인이면 방송이 없어도 먼저 401이다`() {
        mockMvc.post("/api/recordings/999/comments") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"순서 확인"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `공백뿐이거나 500자를 넘는 댓글은 400이고 content 오류를 알려 준다`() {
        val member = mockMvc.signUp("viewer")
        for (content in listOf("   ", "가".repeat(501))) {
            mockMvc.post("/api/recordings/$recordingId/comments") {
                session = member
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                this.content = """{"content":"$content"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("content") }
            }
        }
        mockMvc.post("/api/recordings/$recordingId/comments") {
            session = member
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            this.content = """{"content":"${"가".repeat(500)}"}"""
        }.andExpect { status { isCreated() } }
    }

    private fun expectNoComments() {
        mockMvc.get("/api/recordings/$recordingId/comments").andExpect {
            jsonPath("$.length()") { value(0) }
        }
    }
}
