package com.chzzz.live

import com.chzzz.MutableClock
import com.chzzz.MutableClockConfiguration
import com.chzzz.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant

/**
 * 송출 대조: DB에서 방송 중인데 MediaMTX에 송출이 없는 상태가 유예 시간보다 오래면 종료로 정리한다.
 * MediaMTX 제어 API는 가짜 [MediaServer]로 바꾸고, 주기 작업은 테스트가 직접 한 번씩 돌린다.
 */
@Import(TestcontainersConfiguration::class, MutableClockConfiguration::class, FakeMediaServerConfiguration::class)
@SpringBootTest(
    properties = [
        "mediamtx.api-url=http://mediamtx.invalid:9997",
        "live.publish-grace=30s",
        // 테스트 도중 저절로 돌지 않게 길게 둔다.
        "live.reconcile-interval=1h",
    ],
)
@AutoConfigureMockMvc
class LiveReconcileApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var mediaServer: FakeMediaServer

    @Autowired
    lateinit var reconciler: LiveMediaReconciler

    private val owner by lazy { mockMvc.signUpSomeone() }

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
        clock.setTo(Instant.parse("2026-01-01T00:00:00Z"))
        mediaServer.answer = PublishingPaths.Known(emptySet())
    }

    /** 대기로 시작해 MediaMTX "영상 준비됨" 훅으로 방송 중이 된 라이브 방송. */
    private fun goLive(): Long {
        val live = mockMvc.startLiveAs(owner)
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/${live.id}") }
            .andExpect { status { isOk() } }
        return live.id
    }

    private fun expectStatus(id: Long, status: String) {
        mockMvc.get("/api/live-broadcasts/$id").andExpect { jsonPath("$.status") { value(status) } }
    }

    @Test
    fun `유예 시간이 지나도 MediaMTX에 송출이 없는 방송 중은 종료된다`() {
        val id = goLive()
        clock.advance(Duration.ofSeconds(31))

        reconciler.reconcile()

        mockMvc.get("/api/live-broadcasts/$id").andExpect {
            jsonPath("$.status") { value("ENDED") }
            jsonPath("$.endedAt") { value("2026-01-01T00:00:31Z") }
        }
    }

    @Test
    fun `MediaMTX에 송출이 있는 방송 중은 그대로다`() {
        val id = goLive()
        clock.advance(Duration.ofMinutes(10))
        mediaServer.answer = PublishingPaths.Known(setOf("live/$id"))

        reconciler.reconcile()

        expectStatus(id, "LIVE")
    }

    @Test
    fun `방송 중이 된 직후 MediaMTX 목록에 아직 없어도 유예 시간 안에는 종료하지 않는다`() {
        val id = goLive()
        clock.advance(Duration.ofSeconds(30))

        reconciler.reconcile()
        expectStatus(id, "LIVE")

        clock.advance(Duration.ofSeconds(1))
        reconciler.reconcile()
        expectStatus(id, "ENDED")
    }

    @Test
    fun `MediaMTX에 닿지 않으면 아무 방송도 종료하지 않는다`() {
        val id = goLive()
        clock.advance(Duration.ofMinutes(10))
        mediaServer.answer = PublishingPaths.Unknown("connection refused")

        reconciler.reconcile()

        expectStatus(id, "LIVE")
    }

    @Test
    fun `대기 상태는 송출 대조로 종료하지 않는다`() {
        val live = mockMvc.startLiveAs(owner)
        clock.advance(Duration.ofSeconds(59)) // 대기 시간 초과(60초) 전

        reconciler.reconcile()

        expectStatus(live.id, "PENDING")
    }
}

/** 테스트용 미디어 서버. 테스트가 정한 송출 중 경로(또는 연결 실패)를 그대로 돌려준다. */
class FakeMediaServer : MediaServer {
    @Volatile
    var answer: PublishingPaths = PublishingPaths.Known(emptySet())

    override fun publishingPaths(): PublishingPaths = answer
}

@TestConfiguration(proxyBeanMethods = false)
class FakeMediaServerConfiguration {
    @Bean
    @Primary
    fun fakeMediaServer(): FakeMediaServer = FakeMediaServer()
}
