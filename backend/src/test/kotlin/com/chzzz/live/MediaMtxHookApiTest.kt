package com.chzzz.live

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
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

/** MediaMTX가 보내는 인증·상태 훅 요청을 테스트가 직접 보내 흉내 낸다. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class MediaMtxHookApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
    }

    @Test
    fun `대기 중인 라이브 방송에 맞는 송출 키로 송출하면 허용한다`() {
        val live = startLive()

        auth(action = "publish", path = "live/${live.id}", token = live.streamKey).andExpect { status { isOk() } }
    }

    @Test
    fun `틀린 송출 키로 송출하면 거부한다`() {
        val live = startLive()

        auth(action = "publish", path = "live/${live.id}", token = "wrong-key").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `영상이 들어오면 방송 중이 되고 시작 시각이 남는다`() {
        val live = startLive()

        mockMvc.post("/internal/mediamtx/available") { param("path", "live/${live.id}") }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/live-broadcasts/${live.id}").andExpect {
            jsonPath("$.status") { value("LIVE") }
            jsonPath("$.startedAt") { isString() }
        }
    }

    @Test
    fun `방송 중인 라이브 방송만 시청을 허용한다`() {
        val live = startLive()
        auth(action = "read", path = "live/${live.id}").andExpect { status { isUnauthorized() } }

        mockMvc.post("/internal/mediamtx/available") { param("path", "live/${live.id}") }

        auth(action = "read", path = "live/${live.id}").andExpect { status { isOk() } }
    }

    private fun auth(action: String, path: String, token: String = ""): ResultActionsDsl =
        mockMvc.post("/internal/mediamtx/auth") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"user":"","password":"","token":"$token","ip":"127.0.0.1","action":"$action",
                 "path":"$path","protocol":"webrtc","id":"abc","query":"","userAgent":"test"}
            """.trimIndent()
        }

    private fun startLive(title: String = "훅 테스트 라이브"): Started {
        val body = mockMvc.post("/api/live-broadcasts") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"$title"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val id = Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
        val key = Regex("\"streamKey\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        return Started(id, key)
    }

    data class Started(val id: Long, val streamKey: String)
}
