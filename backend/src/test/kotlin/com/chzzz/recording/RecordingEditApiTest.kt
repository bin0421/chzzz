package com.chzzz.recording

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
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.patch

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class RecordingEditApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    lateinit var owner: MockHttpSession

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording RESTART IDENTITY")
        owner = mockMvc.signUp("owner", "방송주인")
    }

    private fun registerAs(session: MockHttpSession, title: String = "처음 제목"): String =
        mockMvc.multipart("/api/recordings") {
            this.session = session
            with(csrf())
            param("title", title)
            param("url", "https://cdn.example.com/a.mp4")
        }.andExpect { status { isCreated() } }.andReturn().response.getHeader("Location")!!

    private fun patchTitle(location: String, title: String, session: MockHttpSession? = null) =
        mockMvc.patch(location) {
            session?.let { this.session = it }
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"$title"}"""
        }

    @Test
    fun `소유자가 제목을 수정하면 200 상세가 오고 이후 조회에도 새 제목이 나온다`() {
        val location = registerAs(owner)

        patchTitle(location, "  바뀐 제목  ", owner).andExpect {
            status { isOk() }
            jsonPath("$.title") { value("바뀐 제목") }
            jsonPath("$.owner.nickname") { value("방송주인") }
        }

        mockMvc.get(location).andExpect { jsonPath("$.title") { value("바뀐 제목") } }
    }

    @Test
    fun `소유자라도 제목 규칙을 어기면 400이고 제목은 그대로다`() {
        val location = registerAs(owner)

        patchTitle(location, "   ", owner).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("title") }
        }
        patchTitle(location, "가".repeat(101), owner).andExpect { status { isBadRequest() } }

        mockMvc.get(location).andExpect { jsonPath("$.title") { value("처음 제목") } }
    }

    @Test
    fun `비로그인이 제목을 수정하면 401이고 제목은 그대로다`() {
        val location = registerAs(owner)

        patchTitle(location, "남의 제목").andExpect {
            status { isUnauthorized() }
            content { contentType("application/problem+json") }
        }

        mockMvc.get(location).andExpect { jsonPath("$.title") { value("처음 제목") } }
    }

    @Test
    fun `다른 회원이 제목을 수정하면 403이고 제목은 그대로다`() {
        val location = registerAs(owner)
        val other = mockMvc.signUp("other", "다른회원")

        patchTitle(location, "남의 제목", other).andExpect {
            status { isForbidden() }
            content { contentType("application/problem+json") }
        }

        mockMvc.get(location).andExpect { jsonPath("$.title") { value("처음 제목") } }
    }

    @Test
    fun `없는 녹화방송의 제목을 수정하면 로그인한 회원에게 404, 비로그인에게 401이다`() {
        patchTitle("/api/recordings/999", "제목", owner).andExpect { status { isNotFound() } }
        patchTitle("/api/recordings/999", "제목").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `소유자 없는 기존 녹화방송은 어떤 회원도 제목을 수정할 수 없다`() {
        jdbcTemplate.update(
            "INSERT INTO recording (title, source_type, source_value, created_at) VALUES (?, 'EXTERNAL_URL', ?, now())",
            "옛 방송", "https://cdn.example.com/old.mp4",
        )

        patchTitle("/api/recordings/1", "내 것으로", owner).andExpect { status { isForbidden() } }

        mockMvc.get("/api/recordings/1").andExpect { jsonPath("$.title") { value("옛 방송") } }
    }

    @Test
    fun `CSRF 토큰 없이 제목을 수정하면 403이고 제목은 그대로다`() {
        val location = registerAs(owner)

        mockMvc.patch(location) {
            session = owner
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"위조된 요청"}"""
        }.andExpect { status { isForbidden() } }

        mockMvc.get(location).andExpect { jsonPath("$.title") { value("처음 제목") } }
    }
}
