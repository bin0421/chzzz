package com.chzzz.live

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import com.chzzz.MutableClock
import com.chzzz.MutableClockConfiguration
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant

/** 대기 시간 초과(`live.pending-timeout`, 기본 60초): 영상이 들어오지 않은 라이브 방송은 종료된다. */
@Import(TestcontainersConfiguration::class, MutableClockConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class LivePendingTimeoutApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var clock: MutableClock

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
        clock.setTo(Instant.parse("2026-01-01T00:00:00Z"))
    }

    @Test
    fun `대기가 60초를 넘으면 상세 조회 때 종료가 되고 종료 시각이 남는다`() {
        val live = startLive()

        clock.advance(Duration.ofSeconds(61))

        mockMvc.get("/api/live-broadcasts/${live.id}").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ENDED") }
            jsonPath("$.endedAt") { value("2026-01-01T00:01:00Z") }
        }
    }

    @Test
    fun `60초가 되기 전에는 대기로 남고 송출할 수 있다`() {
        val live = startLive()

        clock.advance(Duration.ofSeconds(59))

        mockMvc.get("/api/live-broadcasts/${live.id}").andExpect {
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.endedAt") { doesNotExist() }
        }
        publishAuth(live.id, live.streamKey).andExpect { status { isOk() } }
    }

    @Test
    fun `대기가 60초를 넘으면 목록 조회 때 종료로 저장된다`() {
        val live = startLive()

        clock.advance(Duration.ofSeconds(61))
        mockMvc.get("/api/live-broadcasts").andExpect { status { isOk() } }

        // 종료된 라이브 방송을 목록에 보이는 일은 02가 맡으므로, 여기서는 저장된 상태로 확인한다.
        val stored = jdbcTemplate.queryForMap(
            "SELECT status, ended_at FROM live_broadcast WHERE id = ?", live.id,
        )
        assert(stored["status"] == "ENDED") { "종료로 저장되지 않음: $stored" }
        assert(stored["ended_at"] != null) { "종료 시각이 없음: $stored" }
    }

    @Test
    fun `시간 초과된 대기에는 맞는 송출 키로도 송출할 수 없다`() {
        val live = startLive()

        clock.advance(Duration.ofSeconds(61))

        publishAuth(live.id, live.streamKey).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `시간 초과 뒤에 영상 들어옴 훅이 와도 방송 중이 되지 않는다`() {
        val live = startLive()

        clock.advance(Duration.ofSeconds(61))
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/${live.id}") }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/live-broadcasts/${live.id}").andExpect {
            jsonPath("$.status") { value("ENDED") }
            jsonPath("$.startedAt") { doesNotExist() }
        }
    }

    private fun publishAuth(id: Long, token: String) =
        mockMvc.post("/internal/mediamtx/auth") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"$token","action":"publish","path":"live/$id","protocol":"webrtc"}"""
        }

    private fun startLive(title: String = "시간 초과 라이브"): Started {
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
