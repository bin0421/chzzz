package com.chzzz.comment

import com.chzzz.TestcontainersConfiguration
import com.chzzz.common.BroadcastDeletedEvent
import com.chzzz.common.BroadcastType
import com.chzzz.member.signUp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * 방송이 삭제되면 그 방송의 댓글도 지워진다. 실제 삭제 API(02·03)와는 병합 후 연결을 확인하고,
 * 여기서는 그 API들이 발행할 "방송 삭제됨" 이벤트를 직접 발행한다.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class CommentBroadcastDeletedApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var events: ApplicationEventPublisher

    private lateinit var viewer: MockHttpSession

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording RESTART IDENTITY")
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
        viewer = mockMvc.signUp("viewer")
    }

    @Test
    fun `방송 삭제됨 이벤트를 받으면 그 방송의 댓글만 지운다`() {
        val deleted = insertRecording()
        val kept = insertRecording()
        val liveWithSameId = insertLiveBroadcast()
        check(deleted == liveWithSameId)
        writeComment("recordings", deleted)
        writeComment("recordings", deleted)
        writeComment("recordings", kept)
        writeComment("live-broadcasts", liveWithSameId)

        events.publishEvent(BroadcastDeletedEvent(BroadcastType.RECORDING, deleted))

        assertCommentCount(BroadcastType.RECORDING, deleted, 0)
        mockMvc.get("/api/recordings/$kept/comments").andExpect { jsonPath("$.length()") { value(1) } }
        mockMvc.get("/api/live-broadcasts/$liveWithSameId/comments").andExpect { jsonPath("$.length()") { value(1) } }
    }

    @Test
    fun `라이브 방송 삭제됨 이벤트도 그 방송의 댓글을 지운다`() {
        val live = insertLiveBroadcast()
        writeComment("live-broadcasts", live)

        events.publishEvent(BroadcastDeletedEvent(BroadcastType.LIVE_BROADCAST, live))

        assertCommentCount(BroadcastType.LIVE_BROADCAST, live, 0)
    }

    // 방송 행은 남아 있으므로(삭제 API는 02·03) 목록 API 대신 남은 댓글 수를 센다.
    private fun assertCommentCount(type: BroadcastType, broadcastId: Long, expected: Int) {
        val count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM comment WHERE broadcast_type = ? AND broadcast_id = ?",
            Int::class.java,
            type.name,
            broadcastId,
        )
        check(count == expected) { "남은 댓글 수 $count, 기대 $expected" }
    }

    private fun writeComment(path: String, broadcastId: Long) {
        mockMvc.post("/api/$path/$broadcastId/comments") {
            session = viewer
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"댓글"}"""
        }.andExpect { status { isCreated() } }
    }

    private fun insertRecording(): Long =
        jdbcTemplate.queryForObject(
            "INSERT INTO recording (title, source_type, source_value, created_at) " +
                "VALUES ('녹화', 'EXTERNAL_URL', 'https://cdn.example.com/a.mp4', now()) RETURNING id",
            Long::class.java,
        )!!

    private fun insertLiveBroadcast(): Long =
        jdbcTemplate.queryForObject(
            "INSERT INTO live_broadcast (title, status, stream_key_hash, created_at) " +
                "VALUES ('라이브', 'ENDED', ?, now()) RETURNING id",
            Long::class.java,
            "0".repeat(64),
        )!!
}
