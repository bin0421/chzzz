package com.chzzz.live

import com.chzzz.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper

/** MediaMTX 송출·시청 인증의 거부 규칙. 허용은 송출(대기 + 맞는 키)과 시청(방송 중)뿐이다. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class MediaMtxAuthRulesApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val json = JsonMapper.builder().build()

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
    }

    @Test
    fun `송출 키 없이 송출하면 거부한다`() {
        val live = startLive()

        auth(action = "publish", path = "live/${live.id}", token = null).andExpect { status { isUnauthorized() } }
        auth(action = "publish", path = "live/${live.id}", token = "").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `다른 라이브 방송의 송출 키로는 송출할 수 없다`() {
        val mine = startLive()
        val other = startLive()

        auth(action = "publish", path = "live/${mine.id}", token = other.streamKey)
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `이미 방송 중인 라이브 방송에는 맞는 키로도 다시 송출할 수 없다`() {
        val live = startLive()
        goLive(live.id)

        auth(action = "publish", path = "live/${live.id}", token = live.streamKey)
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `없는 라이브 방송으로는 송출도 시청도 할 수 없다`() {
        val live = startLive()

        auth(action = "publish", path = "live/999", token = live.streamKey).andExpect { status { isUnauthorized() } }
        auth(action = "read", path = "live/999").andExpect { status { isUnauthorized() } }
    }

    @ParameterizedTest
    @ValueSource(strings = ["live/01", "live/1\n", "live/1/", "/live/1", "live/1/x", "live/a", "live/", "other/1", "1", ""])
    fun `live 다음 숫자 식별자 형식이 아닌 경로는 맞는 키로도 거부한다`(path: String) {
        val live = startLive()
        check(live.id == 1L)

        auth(action = "publish", path = path, token = live.streamKey).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `경로가 없으면 거부한다`() {
        val live = startLive()

        auth(action = "publish", path = null, token = live.streamKey).andExpect { status { isUnauthorized() } }
    }

    @ParameterizedTest
    @ValueSource(strings = ["api", "metrics", "pprof", "playback", "PUBLISH", ""])
    fun `송출·시청이 아닌 행동은 거부한다`(action: String) {
        val live = startLive()

        auth(action = action, path = "live/${live.id}", token = live.streamKey)
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `대기 중인 라이브 방송은 시청할 수 없다`() {
        val live = startLive()

        auth(action = "read", path = "live/${live.id}").andExpect { status { isUnauthorized() } }
    }

    private fun auth(action: String, path: String?, token: String? = null): ResultActionsDsl =
        mockMvc.post("/internal/mediamtx/auth") {
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(
                mapOf("user" to "", "password" to "", "token" to token, "ip" to "127.0.0.1",
                    "action" to action, "path" to path, "protocol" to "webrtc", "id" to "abc", "query" to ""),
            )
        }

    private fun goLive(id: Long) {
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/$id") }
            .andExpect { status { isOk() } }
    }

    private fun startLive(title: String = "인증 규칙 라이브"): Started {
        val body = mockMvc.post("/api/live-broadcasts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"$title"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val id = Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
        val key = Regex("\"streamKey\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        return Started(id, key)
    }

    data class Started(val id: Long, val streamKey: String)
}
