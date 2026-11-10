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

/** 송출 키는 생성 응답에서만 나온다. 다른 어떤 응답에도 키 원문이나 해시가 없다. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class StreamKeyExposureApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
    }

    @Test
    fun `대기 상세에는 송출 키와 해시가 없다`() {
        val live = startLive()

        mockMvc.get("/api/live-broadcasts/${live.id}").assertNoSecretOf(live)
    }

    @Test
    fun `방송 중 목록과 상세에는 송출 키와 해시가 없다`() {
        val live = startLive()
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/${live.id}") }

        mockMvc.get("/api/live-broadcasts").andExpect { jsonPath("$[0].id") { value(live.id) } }.assertNoSecretOf(live)
        mockMvc.get("/api/live-broadcasts/${live.id}").assertNoSecretOf(live)
    }

    @Test
    fun `오류 응답과 인증 응답에도 송출 키와 해시가 없다`() {
        val live = startLive()

        mockMvc.get("/api/live-broadcasts/999").andExpect { status { isNotFound() } }.assertNoSecretOf(live)
        mockMvc.get("/api/live-broadcasts/abc").assertNoSecretOf(live)
        mockMvc.post("/api/live-broadcasts") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":""}"""
        }.andExpect { status { isBadRequest() } }.assertNoSecretOf(live)
        mockMvc.post("/internal/mediamtx/auth") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"action":"publish","path":"live/${live.id}","token":"wrong"}"""
        }.andExpect { status { isUnauthorized() } }.assertNoSecretOf(live)
    }

    private fun ResultActionsDsl.assertNoSecretOf(live: Started) {
        val response = andReturn().response
        val everything = response.contentAsString + response.headerNames.joinToString { "$it=${response.getHeaders(it)}" }
        assert(!everything.contains(live.streamKey)) { "송출 키가 응답에 있음: $everything" }
        assert(!everything.contains(live.hash)) { "송출 키 해시가 응답에 있음: $everything" }
        assert(!everything.contains("streamKey") && !everything.contains("streamKeyHash")) {
            "송출 키 필드가 응답에 있음: $everything"
        }
    }

    private fun startLive(title: String = "노출 확인 라이브"): Started {
        val body = mockMvc.post("/api/live-broadcasts") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"$title"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val id = Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
        val key = Regex("\"streamKey\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        val hash = jdbcTemplate.queryForObject("SELECT stream_key_hash FROM live_broadcast WHERE id = ?", String::class.java, id)!!
        return Started(id, key, hash)
    }

    data class Started(val id: Long, val streamKey: String, val hash: String)
}
