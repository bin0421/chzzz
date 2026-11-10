package com.chzzz.live

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import com.chzzz.TestcontainersConfiguration
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.not
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

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class LiveBroadcastApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
    }

    @Test
    fun `라이브 방송을 시작하면 201과 대기 상태, 송출 키, WHIP 주소를 돌려준다`() {
        mockMvc.post("/api/live-broadcasts") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"저녁 산책 라이브"}"""
        }.andExpect {
            status { isCreated() }
            header { string("Location", matchesPattern("/api/live-broadcasts/\\d+")) }
            jsonPath("$.id") { isNumber() }
            jsonPath("$.title") { value("저녁 산책 라이브") }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.streamKey") { value(matchesPattern("[A-Za-z0-9_-]{32,}")) }
            jsonPath("$.whipUrl") { value(matchesPattern("/media/live/\\d+/whip")) }
            jsonPath("$.createdAt") { isString() }
        }
    }

    @Test
    fun `송출 키는 DB에 원문이 아닌 해시로만 저장된다`() {
        val streamKey = startLive("해시 확인 라이브").streamKey

        val stored = jdbcTemplate.queryForObject("SELECT stream_key_hash FROM live_broadcast", String::class.java)!!
        assert(stored != streamKey) { "송출 키 원문이 저장됨" }
        assert(stored.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 hex가 아님: $stored" }
    }

    @Test
    fun `제목이 비어 있으면 400으로 title 오류를 돌려주고 만들지 않는다`() {
        mockMvc.post("/api/live-broadcasts") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"   "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("title") }
            jsonPath("$.errors[0].message") { value("제목을 입력해 주세요.") }
        }

        val count = jdbcTemplate.queryForObject("SELECT count(*) FROM live_broadcast", Long::class.java)
        assert(count == 0L) { "라이브 방송이 만들어짐: $count" }
    }

    @Test
    fun `상세는 상태와 WHEP 주소를 주고 송출 키는 주지 않는다`() {
        val live = startLive("상세 라이브")

        mockMvc.get("/api/live-broadcasts/${live.id}").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(live.id) }
            jsonPath("$.title") { value("상세 라이브") }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.whepUrl") { value("/media/live/${live.id}/whep") }
            jsonPath("$.createdAt") { isString() }
            jsonPath("$.streamKey") { doesNotExist() }
            jsonPath("$.streamKeyHash") { doesNotExist() }
            content { string(not(containsString(live.streamKey))) }
        }
    }

    @Test
    fun `없는 라이브 방송의 상세는 404다`() {
        mockMvc.get("/api/live-broadcasts/9999").andExpect {
            status { isNotFound() }
            content { contentType("application/problem+json") }
        }
    }

    @Test
    fun `목록에는 방송 중인 라이브 방송만 나오고 대기는 나오지 않는다`() {
        startLive("아직 대기 중")
        val onAir = startLive("방송 중인 라이브")
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/${onAir.id}") }

        mockMvc.get("/api/live-broadcasts").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(onAir.id) }
            jsonPath("$[0].title") { value("방송 중인 라이브") }
            jsonPath("$[0].status") { value("LIVE") }
            jsonPath("$[0].startedAt") { isString() }
            jsonPath("$[0].streamKey") { doesNotExist() }
        }
    }

    private fun startLive(title: String): StartedLive {
        val body = mockMvc.post("/api/live-broadcasts") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"$title"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val id = Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
        val key = Regex("\"streamKey\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        return StartedLive(id, key)
    }

    data class StartedLive(val id: Long, val streamKey: String)
}
