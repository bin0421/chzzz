package com.chzzz.live

import com.chzzz.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** MediaMTX `runOnUnavailable` 훅(영상 끊김)을 테스트가 직접 보내 흉내 낸다. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class MediaMtxUnavailableHookApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
    }

    @Test
    fun `방송 중에 영상이 끊기면 종료되고 종료 시각이 남는다`() {
        val id = startLive()
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/$id") }

        unavailable("live/$id").andExpect { status { isOk() } }

        mockMvc.get("/api/live-broadcasts/$id").andExpect {
            jsonPath("$.status") { value("ENDED") }
            jsonPath("$.endedAt") { isString() }
        }
    }

    @Test
    fun `대기 중에 영상이 끊겨도 종료된다`() {
        val id = startLive()

        unavailable("live/$id").andExpect { status { isOk() } }

        mockMvc.get("/api/live-broadcasts/$id").andExpect {
            jsonPath("$.status") { value("ENDED") }
            jsonPath("$.endedAt") { isString() }
        }
    }

    @Test
    fun `이미 종료된 방송에 온 끊김 훅은 무시하고 200을 준다`() {
        val id = startLive()
        unavailable("live/$id")
        val firstEndedAt = endedAtOf(id)

        unavailable("live/$id").andExpect { status { isOk() } }

        assert(endedAtOf(id) == firstEndedAt) { "종료 시각이 바뀜" }
    }

    @Test
    fun `라이브 방송 경로가 아니거나 없는 방송의 끊김 훅도 200이다`() {
        unavailable("other/path").andExpect { status { isOk() } }
        unavailable("live/9999").andExpect { status { isOk() } }
    }

    private fun unavailable(path: String): ResultActionsDsl =
        mockMvc.post("/internal/mediamtx/unavailable") { param("path", path) }

    private fun endedAtOf(id: Long): String =
        Regex("\"endedAt\":\"([^\"]+)\"")
            .find(mockMvc.get("/api/live-broadcasts/$id").andReturn().response.contentAsString)!!
            .groupValues[1]

    private fun startLive(): Long {
        val body = mockMvc.post("/api/live-broadcasts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"끊김 테스트 라이브"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }
}
