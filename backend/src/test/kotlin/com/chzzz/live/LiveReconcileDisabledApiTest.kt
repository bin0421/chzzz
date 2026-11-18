package com.chzzz.live

import com.chzzz.MutableClock
import com.chzzz.MutableClockConfiguration
import com.chzzz.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant

/** MediaMTX 제어 API 주소를 비우면(개발 실행 기본값) 송출 대조를 하지 않는다. */
@Import(TestcontainersConfiguration::class, MutableClockConfiguration::class, FakeMediaServerConfiguration::class)
@SpringBootTest(properties = ["mediamtx.api-url=", "live.reconcile-interval=1h"])
@AutoConfigureMockMvc
class LiveReconcileDisabledApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var mediaServer: FakeMediaServer

    @Autowired
    lateinit var reconciler: LiveMediaReconciler

    @Test
    fun `MediaMTX 제어 API 주소가 없으면 송출이 없어도 방송 중을 종료하지 않는다`() {
        clock.setTo(Instant.parse("2026-01-01T00:00:00Z"))
        val live = mockMvc.startLiveAs(mockMvc.signUpSomeone())
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/${live.id}") }
        clock.advance(Duration.ofMinutes(10))
        mediaServer.answer = PublishingPaths.Known(emptySet())

        reconciler.reconcile()

        mockMvc.get("/api/live-broadcasts/${live.id}").andExpect { jsonPath("$.status") { value("LIVE") } }
    }
}
