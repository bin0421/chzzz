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

/** 송출자 종료 API: 3단계부터는 로그인한 소유자만 라이브 방송을 종료할 수 있다(ADR-0006). */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class LiveBroadcastEndApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    // 3단계부터 라이브 방송 시작에는 로그인이 필요하다.
    private val owner by lazy { mockMvc.signUpSomeone() }

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
    }

    // 3단계(ADR-0006): 종료 근거가 송출 키에서 소유자 세션으로 바뀌어 요청을 소유자 세션으로 보낸다.

    @Test
    fun `소유자가 방송 중인 라이브 방송을 종료하면 200과 종료 상태, 종료 시각을 준다`() {
        val live = startLive()
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/${live.id}") }

        end(live.id).andExpect {
            status { isOk() }
            jsonPath("$.id") { value(live.id) }
            jsonPath("$.status") { value("ENDED") }
            jsonPath("$.startedAt") { isString() }
            jsonPath("$.endedAt") { isString() }
            jsonPath("$.streamKey") { doesNotExist() }
        }
    }

    @Test
    fun `송출 키만으로는 종료할 수 없고 방송은 그대로다`() {
        val live = startLive()

        mockMvc.post("/api/live-broadcasts/${live.id}/end") {
            with(csrf())
            header("Authorization", "Bearer ${live.streamKey}")
        }.andExpect {
            status { isUnauthorized() }
            content { contentType("application/problem+json") }
        }

        mockMvc.get("/api/live-broadcasts/${live.id}").andExpect {
            jsonPath("$.status") { value("PENDING") }
        }
    }

    @Test
    fun `이미 종료된 라이브 방송을 다시 종료하면 409다`() {
        val live = startLive()
        end(live.id).andExpect { status { isOk() } }

        end(live.id).andExpect {
            status { isConflict() }
            content { contentType("application/problem+json") }
        }
    }

    @Test
    fun `없는 라이브 방송을 종료하면 404다`() {
        end(9999).andExpect {
            status { isNotFound() }
            content { contentType("application/problem+json") }
        }
    }

    private fun end(id: Long): ResultActionsDsl =
        mockMvc.post("/api/live-broadcasts/$id/end") {
            session = owner
            with(csrf())
        }

    private fun startLive(title: String = "종료 테스트 라이브"): Started {
        val body = mockMvc.post("/api/live-broadcasts") {
            with(csrf())
            session = owner
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"$title"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val id = Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
        val key = Regex("\"streamKey\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        return Started(id, key)
    }

    data class Started(val id: Long, val streamKey: String)
}
