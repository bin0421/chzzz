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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** 방송 목록의 라이브 방송 순서: 방송 중(최근 시작순) → 종료 최근 10개(최근 종료순). 대기는 없다. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class LiveBroadcastListOrderApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
    }

    @Test
    fun `방송 중을 최근 시작순으로 먼저, 이어서 종료 중 최근 10개를 최근 종료순으로 준다`() {
        // 종료될 방송 12개를 먼저 만들어 두고 거꾸로 종료한다: 만든 순서와 종료 순서가 다르다.
        val toEnd = (1..12).map { startLive("종료 $it") }
        toEnd.forEach { goLive(it) }
        toEnd.reversed().forEach { endByDisconnect(it) } // 종료 12 → 종료 1 순으로 끝남
        startLive("대기")
        val onAir = (1..3).map { startLive("방송 중 $it") }
        onAir.reversed().forEach { goLive(it) } // 방송 중 3 → 방송 중 1 순으로 시작

        val titles = Regex("\"title\":\"([^\"]+)\"")
            .findAll(mockMvc.get("/api/live-broadcasts").andReturn().response.contentAsString)
            .map { it.groupValues[1] }
            .toList()

        assert(
            titles == listOf("방송 중 1", "방송 중 2", "방송 중 3") + (1..10).map { "종료 $it" }
        ) { "목록 순서가 다름: $titles" }
    }

    private fun goLive(id: Long) {
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/$id") }
    }

    private fun endByDisconnect(id: Long) {
        mockMvc.post("/internal/mediamtx/unavailable") { param("path", "live/$id") }
    }

    private fun startLive(title: String): Long {
        val body = mockMvc.post("/api/live-broadcasts") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"$title"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }
}
