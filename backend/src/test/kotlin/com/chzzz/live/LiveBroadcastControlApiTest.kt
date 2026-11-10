package com.chzzz.live

import com.chzzz.TestcontainersConfiguration
import com.chzzz.common.BroadcastDeletedEvent
import com.chzzz.common.BroadcastType
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
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

/**
 * 라이브 방송 제어(종료·제목 수정·삭제)는 로그인한 소유자만 한다.
 * 비로그인 401 → 없음 404 → 소유자 아님 403 → 상태 충돌 409. 송출 키는 제어 근거가 아니다.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class LiveBroadcastControlApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var events: ApplicationEvents

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    lateinit var owner: MockHttpSession
    lateinit var other: MockHttpSession

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
        owner = mockMvc.signUp("owner", "소유자")
        other = mockMvc.signUp("other", "다른회원")
    }

    // --- 종료 ---

    @Test
    fun `소유자는 송출하던 탭이 아니어도 세션만으로 방송 중인 라이브 방송을 종료한다`() {
        val live = mockMvc.startLiveAs(owner)
        goLive(live.id)
        val anotherTabOfOwner = mockMvc.loginAs("owner")

        end(live.id, anotherTabOfOwner).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ENDED") }
            jsonPath("$.endedAt") { isString() }
            jsonPath("$.owner.nickname") { value("소유자") }
        }
    }

    @Test
    fun `비로그인이 종료하면 송출 키를 실어 보내도 401이고 방송은 그대로다`() {
        val live = mockMvc.startLiveAs(owner)
        goLive(live.id)

        end(live.id, session = null, streamKey = live.streamKey).andExpect {
            status { isUnauthorized() }
            content { contentType("application/problem+json") }
        }

        assertStatus(live.id, "LIVE")
    }

    @Test
    fun `다른 회원이 종료하면 송출 키를 실어 보내도 403이고 방송은 그대로다`() {
        val live = mockMvc.startLiveAs(owner)
        goLive(live.id)

        end(live.id, other, streamKey = live.streamKey).andExpect {
            status { isForbidden() }
            content { contentType("application/problem+json") }
        }

        assertStatus(live.id, "LIVE")
    }

    @Test
    fun `소유자 없는 기존 라이브 방송은 아무도 종료할 수 없다`() {
        val id = insertOwnerlessLive("LIVE")

        end(id, owner).andExpect { status { isForbidden() } }

        assertStatus(id, "LIVE")
    }

    @Test
    fun `없는 라이브 방송을 종료하면 404, 이미 종료된 방송을 다시 종료하면 409다`() {
        end(9999, owner).andExpect { status { isNotFound() } }

        val live = mockMvc.startLiveAs(owner)
        end(live.id, owner).andExpect { status { isOk() } }
        end(live.id, owner).andExpect {
            status { isConflict() }
            content { contentType("application/problem+json") }
        }
    }

    @Test
    fun `비로그인은 대상이 없어도 404보다 401을 먼저 받는다`() {
        end(9999, session = null).andExpect { status { isUnauthorized() } }
    }

    // --- 제목 수정 ---

    @Test
    fun `소유자는 대기·방송 중·종료 모두에서 제목을 수정하고 목록·상세에 반영된다`() {
        val live = mockMvc.startLiveAs(owner, "처음 제목")

        rename(live.id, owner, "  대기 중 제목  ").andExpect {
            status { isOk() }
            jsonPath("$.title") { value("대기 중 제목") }
            jsonPath("$.status") { value("PENDING") }
        }
        goLive(live.id)
        rename(live.id, owner, "방송 중 제목").andExpect { status { isOk() } }
        mockMvc.get("/api/live-broadcasts").andExpect { jsonPath("$[0].title") { value("방송 중 제목") } }
        end(live.id, owner)
        rename(live.id, owner, "종료 후 제목").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ENDED") }
        }

        mockMvc.get("/api/live-broadcasts/${live.id}").andExpect { jsonPath("$.title") { value("종료 후 제목") } }
    }

    @Test
    fun `비로그인 401, 다른 회원 403, 소유자 없는 방송 403, 없는 방송 404이고 제목은 그대로다`() {
        val live = mockMvc.startLiveAs(owner, "원래 제목")
        val ownerless = insertOwnerlessLive("ENDED")

        rename(live.id, null, "바꿈").andExpect { status { isUnauthorized() } }
        rename(live.id, other, "바꿈").andExpect {
            status { isForbidden() }
            content { contentType("application/problem+json") }
        }
        rename(ownerless, owner, "바꿈").andExpect { status { isForbidden() } }
        rename(9999, owner, "바꿈").andExpect { status { isNotFound() } }

        mockMvc.get("/api/live-broadcasts/${live.id}").andExpect { jsonPath("$.title") { value("원래 제목") } }
        mockMvc.get("/api/live-broadcasts/$ownerless").andExpect { jsonPath("$.title") { value("옛 라이브") } }
    }

    @Test
    fun `빈 제목으로 수정하면 400으로 title 오류를 주고 제목은 그대로다`() {
        val live = mockMvc.startLiveAs(owner, "원래 제목")

        rename(live.id, owner, "   ").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("title") }
        }

        mockMvc.get("/api/live-broadcasts/${live.id}").andExpect { jsonPath("$.title") { value("원래 제목") } }
    }

    // --- 삭제 ---

    @Test
    fun `소유자가 종료된 라이브 방송을 삭제하면 204이고 목록·상세에서 사라지며 방송 삭제됨 이벤트가 나간다`() {
        val live = mockMvc.startLiveAs(owner)
        goLive(live.id)
        end(live.id, owner)

        delete(live.id, owner).andExpect { status { isNoContent() } }

        mockMvc.get("/api/live-broadcasts/${live.id}").andExpect { status { isNotFound() } }
        mockMvc.get("/api/live-broadcasts").andExpect { jsonPath("$.length()") { value(0) } }
        assertThat(events.stream(BroadcastDeletedEvent::class.java))
            .containsExactly(BroadcastDeletedEvent(BroadcastType.LIVE_BROADCAST, live.id))
    }

    @Test
    fun `대기·방송 중인 라이브 방송은 소유자라도 삭제하면 409이고 남아 있다`() {
        val pending = mockMvc.startLiveAs(owner, "대기")
        val onAir = mockMvc.startLiveAs(owner, "방송 중")
        goLive(onAir.id)

        for (id in listOf(pending.id, onAir.id)) {
            delete(id, owner).andExpect {
                status { isConflict() }
                content { contentType("application/problem+json") }
            }
        }

        assertStatus(pending.id, "PENDING")
        assertStatus(onAir.id, "LIVE")
        assertThat(events.stream(BroadcastDeletedEvent::class.java)).isEmpty()
    }

    @Test
    fun `삭제는 비로그인 401, 다른 회원 403, 소유자 없는 방송 403, 없는 방송 404이고 방송은 남아 있다`() {
        val live = mockMvc.startLiveAs(owner)
        end(live.id, owner)
        val ownerless = insertOwnerlessLive("ENDED")

        delete(live.id, null).andExpect { status { isUnauthorized() } }
        delete(live.id, other).andExpect { status { isForbidden() } }
        delete(ownerless, owner).andExpect { status { isForbidden() } }
        delete(9999, owner).andExpect { status { isNotFound() } }

        assertStatus(live.id, "ENDED")
        assertStatus(ownerless, "ENDED")
        assertThat(events.stream(BroadcastDeletedEvent::class.java)).isEmpty()
    }

    // --- 도우미 ---

    private fun delete(id: Long, session: MockHttpSession?): ResultActionsDsl =
        mockMvc.delete("/api/live-broadcasts/$id") {
            if (session != null) this.session = session
            with(csrf())
        }

    private fun rename(id: Long, session: MockHttpSession?, title: String): ResultActionsDsl =
        mockMvc.patch("/api/live-broadcasts/$id") {
            if (session != null) this.session = session
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"$title"}"""
        }

    private fun end(id: Long, session: MockHttpSession?, streamKey: String? = null): ResultActionsDsl =
        mockMvc.post("/api/live-broadcasts/$id/end") {
            if (session != null) this.session = session
            with(csrf())
            if (streamKey != null) header("Authorization", "Bearer $streamKey")
        }

    private fun goLive(id: Long) {
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/$id") }
    }

    private fun assertStatus(id: Long, status: String) {
        mockMvc.get("/api/live-broadcasts/$id").andExpect { jsonPath("$.status") { value(status) } }
    }

    private fun insertOwnerlessLive(status: String): Long {
        jdbcTemplate.update(
            "INSERT INTO live_broadcast (title, stream_key_hash, status, created_at, started_at) " +
                "VALUES ('옛 라이브', ?, ?, now(), now())",
            "0".repeat(64), status,
        )
        return jdbcTemplate.queryForObject("SELECT max(id) FROM live_broadcast", Long::class.java)!!
    }
}
