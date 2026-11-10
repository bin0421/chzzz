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
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant

/** 아무도 조회하지 않아도 주기 정리 작업이 시간 초과된 대기를 종료로 정리한다. */
@Import(TestcontainersConfiguration::class, MutableClockConfiguration::class)
@SpringBootTest(properties = ["live.pending-sweep-interval=200ms"])
@AutoConfigureMockMvc
class LivePendingSweepTest {

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
    fun `조회가 없어도 주기 정리 작업이 시간 초과된 대기를 종료로 바꾼다`() {
        mockMvc.post("/api/live-broadcasts") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"아무도 보지 않는 라이브"}"""
        }.andExpect { status { isCreated() } }

        clock.advance(Duration.ofSeconds(61))

        // 공개 API로 조회하면 그 자체가 정리를 일으키므로 저장된 상태를 직접 기다린다.
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        var status: String? = null
        while (System.nanoTime() < deadline) {
            status = jdbcTemplate.queryForObject("SELECT status FROM live_broadcast", String::class.java)
            if (status == "ENDED") break
            Thread.sleep(100)
        }
        assert(status == "ENDED") { "5초 안에 정리되지 않음: $status" }
    }
}
