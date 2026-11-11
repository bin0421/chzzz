package com.chzzz.donation

import com.chzzz.TestcontainersConfiguration
import com.chzzz.live.startLiveAs
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post

/** 후원 시작 규칙: 로그인, 금액, 대상 방송. 거절되면 후원이 저장되지 않는다. */
@Import(TestcontainersConfiguration::class, FakePaymentGatewayConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class DonationStartRulesApiTest {
    companion object {
        const val AMOUNT = """{"amount":5000}"""
    }


    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    lateinit var streamer: MockHttpSession
    lateinit var donor: MockHttpSession

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording, live_broadcast RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE donation, payment_notification RESTART IDENTITY")
        streamer = mockMvc.signUp("streamer", "방송자")
        donor = mockMvc.signUp("donor", "후원자")
    }

    @Test
    fun `금액이 1,000원 미만, 1,000,000원 초과, 소수, 숫자가 아니면 400이고 amount 오류를 주며 후원이 저장되지 않는다`() {
        val recordingId = registerRecording()

        for (amount in listOf("999", "1000001", "1000.5", "\"천원\"", "null")) {
            start("/api/recordings/$recordingId/donations", donor, """{"amount":$amount}""").andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("amount") }
                jsonPath("$.errors[0].message") { isString() }
            }
        }

        assertThat(donationCount()).isZero()
    }

    @Test
    fun `경계 금액 1,000원과 1,000,000원은 녹화방송과 대기·방송 중·종료된 라이브 방송 모두 201이다`() {
        val recordingId = registerRecording()
        val pending = mockMvc.startLiveAs(streamer, "대기 라이브").id
        val onAir = mockMvc.startLiveAs(streamer, "방송 중 라이브").id
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/$onAir") }
        val ended = mockMvc.startLiveAs(streamer, "종료된 라이브").id
        mockMvc.post("/api/live-broadcasts/$ended/end") {
            session = streamer
            with(csrf())
        }.andExpect { status { isOk() } }
        assertThat(liveStatuses(pending, onAir, ended)).containsExactly("PENDING", "LIVE", "ENDED")

        val paths = listOf("/api/recordings/$recordingId") + listOf(pending, onAir, ended).map { "/api/live-broadcasts/$it" }
        for (path in paths) {
            for (amount in listOf(1000, 1000000)) {
                start("$path/donations", donor, """{"amount":$amount}""").andExpect {
                    status { isCreated() }
                    jsonPath("$.amount") { value(amount) }
                }
            }
        }

        assertThat(donationCount()).isEqualTo(8)
    }

    @Test
    fun `비로그인은 401, 없는 방송은 404이고 후원이 저장되지 않는다`() {
        val recordingId = registerRecording()

        start("/api/recordings/$recordingId/donations", null, AMOUNT).andExpect { status { isUnauthorized() } }
        start("/api/recordings/9999/donations", donor, AMOUNT).andExpect { status { isNotFound() } }
        start("/api/live-broadcasts/9999/donations", donor, AMOUNT).andExpect { status { isNotFound() } }

        assertThat(donationCount()).isZero()
    }

    @Test
    fun `CSRF 토큰 없이 후원을 시작하면 403이고 후원이 저장되지 않는다`() {
        val recordingId = registerRecording()

        mockMvc.post("/api/recordings/$recordingId/donations") {
            session = donor
            contentType = MediaType.APPLICATION_JSON
            content = AMOUNT
        }.andExpect { status { isForbidden() } }

        assertThat(donationCount()).isZero()
    }

    private fun liveStatuses(vararg ids: Long): List<String> =
        ids.map { jdbcTemplate.queryForObject("SELECT status FROM live_broadcast WHERE id = ?", String::class.java, it)!! }

    @Test
    fun `소유자 없는 방송과 자기 방송에는 후원을 시작할 수 없어 409이고 후원이 저장되지 않는다`() {
        val ownRecording = registerRecording()
        val ownLive = mockMvc.startLiveAs(streamer, "내 라이브")
        val ownerlessRecording = insertOwnerlessRecording()
        val ownerlessLive = insertOwnerlessLive("ENDED")

        start("/api/recordings/$ownRecording/donations", streamer, AMOUNT).andExpect { status { isConflict() } }
        start("/api/live-broadcasts/${ownLive.id}/donations", streamer, AMOUNT).andExpect { status { isConflict() } }
        start("/api/recordings/$ownerlessRecording/donations", donor, AMOUNT).andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("후원할 수 없는 방송입니다.") }
        }
        start("/api/live-broadcasts/$ownerlessLive/donations", donor, AMOUNT).andExpect { status { isConflict() } }

        assertThat(donationCount()).isZero()
    }

    private fun insertOwnerlessRecording(): Long {
        jdbcTemplate.update(
            "INSERT INTO recording (title, source_type, source_value, created_at) VALUES ('옛 방송', 'EXTERNAL_URL', ?, now())",
            "https://cdn.example.com/old.mp4",
        )
        return jdbcTemplate.queryForObject("SELECT max(id) FROM recording", Long::class.java)!!
    }

    private fun insertOwnerlessLive(status: String): Long {
        jdbcTemplate.update(
            "INSERT INTO live_broadcast (title, stream_key_hash, status, created_at, started_at) " +
                "VALUES ('옛 라이브', ?, ?, now(), now())",
            "0".repeat(64), status,
        )
        return jdbcTemplate.queryForObject("SELECT max(id) FROM live_broadcast", Long::class.java)!!
    }

    private fun start(path: String, session: MockHttpSession?, body: String): ResultActionsDsl =
        mockMvc.post(path) {
            if (session != null) this.session = session
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun donationCount(): Int = jdbcTemplate.queryForObject("SELECT count(*) FROM donation", Int::class.java)!!

    private fun registerRecording(): Long =
        mockMvc.multipart("/api/recordings") {
            session = streamer
            with(csrf())
            param("title", "좋은 방송")
            param("url", "https://cdn.example.com/video.mp4")
        }.andExpect { status { isCreated() } }
            .andReturn().response.getHeader("Location")!!.substringAfterLast('/').toLong()
}
