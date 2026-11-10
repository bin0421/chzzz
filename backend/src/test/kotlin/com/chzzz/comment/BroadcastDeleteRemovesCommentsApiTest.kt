package com.chzzz.comment

import com.chzzz.TestcontainersConfiguration
import com.chzzz.live.startLiveAs
import com.chzzz.member.signUp
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post

/**
 * 02·03의 방송 삭제 API와 04의 댓글 정리가 "방송 삭제됨" 이벤트로 실제로 이어지는지 확인한다(병합 후 통합 확인).
 * 다른 방송의 댓글은 남아야 한다.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class BroadcastDeleteRemovesCommentsApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    lateinit var owner: MockHttpSession
    lateinit var viewer: MockHttpSession

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        owner = mockMvc.signUp("owner", "소유자")
        viewer = mockMvc.signUp("viewer", "시청자")
    }

    @Test
    fun `녹화방송을 삭제하면 그 녹화방송의 댓글도 지워지고 다른 녹화방송 댓글은 남는다`() {
        val deleted = registerRecording("지울 방송")
        val kept = registerRecording("남길 방송")
        comment("/api/recordings/$deleted/comments", "지워질 댓글")
        comment("/api/recordings/$kept/comments", "남을 댓글")

        mockMvc.delete("/api/recordings/$deleted") {
            session = owner
            with(csrf())
        }.andExpect { status { isNoContent() } }

        assertThat(commentContents()).containsExactly("남을 댓글")
    }

    @Test
    fun `종료된 라이브 방송을 삭제하면 그 방송의 댓글도 지워진다`() {
        val live = mockMvc.startLiveAs(owner, "지울 라이브")
        comment("/api/live-broadcasts/${live.id}/comments", "라이브 댓글")
        mockMvc.post("/api/live-broadcasts/${live.id}/end") {
            session = owner
            with(csrf())
        }.andExpect { status { isOk() } }

        mockMvc.delete("/api/live-broadcasts/${live.id}") {
            session = owner
            with(csrf())
        }.andExpect { status { isNoContent() } }

        assertThat(commentContents()).isEmpty()
    }

    private fun registerRecording(title: String): Long {
        val location = mockMvc.multipart("/api/recordings") {
            session = owner
            with(csrf())
            param("title", title)
            param("url", "https://cdn.example.com/video-${title.hashCode()}.mp4")
        }.andExpect { status { isCreated() } }.andReturn().response.getHeader("Location")!!
        return location.substringAfterLast('/').toLong()
    }

    private fun comment(path: String, content: String) {
        mockMvc.post(path) {
            session = viewer
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            this.content = """{"content":"$content"}"""
        }.andExpect { status { isCreated() } }
    }

    private fun commentContents(): List<String?> =
        jdbcTemplate.queryForList("SELECT content FROM comment ORDER BY id", String::class.java)
}
