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

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class CommentApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording RESTART IDENTITY")
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
    }

    @Test
    fun `로그인한 회원이 녹화방송에 쓴 댓글이 누구나 보는 목록에 오래된 순으로 나온다`() {
        val recordingId = insertRecording()
        val viewer = mockMvc.signUp("viewer", "시청자")

        mockMvc.post("/api/recordings/$recordingId/comments") {
            session = viewer
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"  첫 댓글  "}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { isNumber() }
            jsonPath("$.content") { value("첫 댓글") }
            jsonPath("$.author.nickname") { value("시청자") }
            jsonPath("$.createdAt") { isString() }
            jsonPath("$.updatedAt") { value(null as Any?) }
        }
        mockMvc.post("/api/recordings/$recordingId/comments") {
            session = viewer
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"둘째 댓글"}"""
        }.andExpect { status { isCreated() } }

        mockMvc.get("/api/recordings/$recordingId/comments").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].content") { value("첫 댓글") }
            jsonPath("$[1].content") { value("둘째 댓글") }
            jsonPath("$[0].author.id") { isNumber() }
            jsonPath("$[0].author.loginId") { doesNotExist() }
        }
    }

    @Test
    fun `종료된 라이브 방송에도 댓글을 쓰고 누구나 목록을 본다`() {
        val liveId = insertLiveBroadcast("ENDED")
        val viewer = mockMvc.signUp("viewer", "시청자")

        mockMvc.post("/api/live-broadcasts/$liveId/comments") {
            session = viewer
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"다시 보고 싶어요"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.author.nickname") { value("시청자") }
        }

        mockMvc.get("/api/live-broadcasts/$liveId/comments").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].content") { value("다시 보고 싶어요") }
        }
    }

    @Test
    fun `같은 식별자라도 녹화방송과 라이브 방송의 댓글은 섞이지 않는다`() {
        val recordingId = insertRecording()
        val liveId = insertLiveBroadcast("LIVE")
        check(recordingId == liveId)
        val viewer = mockMvc.signUp("viewer")

        mockMvc.post("/api/recordings/$recordingId/comments") {
            session = viewer
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"녹화방송 댓글"}"""
        }.andExpect { status { isCreated() } }

        mockMvc.get("/api/live-broadcasts/$liveId/comments").andExpect {
            jsonPath("$.length()") { value(0) }
        }
    }

    private fun insertLiveBroadcast(status: String): Long =
        jdbcTemplate.queryForObject(
            "INSERT INTO live_broadcast (title, status, stream_key_hash, created_at) " +
                "VALUES ('라이브', ?, ?, now()) RETURNING id",
            Long::class.java,
            status,
            "0".repeat(64),
        )!!

    private fun insertRecording(): Long =
        jdbcTemplate.queryForObject(
            "INSERT INTO recording (title, source_type, source_value, created_at) " +
                "VALUES ('녹화', 'EXTERNAL_URL', 'https://cdn.example.com/a.mp4', now()) RETURNING id",
            Long::class.java,
        )!!
}
