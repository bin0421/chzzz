package com.chzzz.donation

import com.chzzz.TestcontainersConfiguration
import com.chzzz.member.signUp
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import java.util.UUID

/** 방송별 후원 내역·합계: 누구나 보고, 확정된 후원만 최신순으로 센다. */
@Import(TestcontainersConfiguration::class, FakePaymentGatewayConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class DonationListRulesApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var gateway: FakePaymentGateway

    lateinit var streamer: MockHttpSession

    @BeforeEach
    fun setUp() {
        gateway.reset()
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording, live_broadcast RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE donation, payment_notification RESTART IDENTITY")
        streamer = mockMvc.signUp("streamer", "방송자")
    }

    @Test
    fun `없는 방송의 후원 내역은 404다`() {
        mockMvc.get("/api/recordings/9999/donations").andExpect { status { isNotFound() } }
        mockMvc.get("/api/live-broadcasts/9999/donations").andExpect { status { isNotFound() } }
    }

    @Test
    fun `비로그인도 내역을 보고, 확정된 후원만 최신순으로 합계·건수에 들어간다`() {
        val recordingId = registerRecording()
        val first = mockMvc.signUp("first", "첫후원자")
        val second = mockMvc.signUp("second", "둘째후원자")
        confirmDonation(first, recordingId, 1000)
        confirmDonation(second, recordingId, 50000)
        for (status in listOf("AWAITING_PAYMENT", "FAILED", "CANCELED", "NEEDS_REVIEW")) {
            insertDonation(recordingId, status, 7000)
        }

        mockMvc.get("/api/recordings/$recordingId/donations").andExpect {
            status { isOk() }
            jsonPath("$.totalAmount") { value(51000) }
            jsonPath("$.count") { value(2) }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].donor.nickname") { value("둘째후원자") }
            jsonPath("$.items[0].amount") { value(50000) }
            jsonPath("$.items[1].donor.nickname") { value("첫후원자") }
        }
    }

    @Test
    fun `후원이 없는 방송은 합계 0원, 0건이다`() {
        val recordingId = registerRecording()

        mockMvc.get("/api/recordings/$recordingId/donations").andExpect {
            status { isOk() }
            jsonPath("$.totalAmount") { value(0) }
            jsonPath("$.count") { value(0) }
            jsonPath("$.items.length()") { value(0) }
        }
    }

    private fun confirmDonation(donor: MockHttpSession, recordingId: Long, amount: Int) {
        val body = mockMvc.post("/api/recordings/$recordingId/donations") {
            session = donor
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":$amount}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val orderId = Regex("\"orderId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        mockMvc.post("/api/donations/confirm") {
            session = donor
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"paymentKey":"pay_$orderId","orderId":"$orderId","amount":$amount}"""
        }.andExpect { jsonPath("$.status") { value("CONFIRMED") } }
    }

    /** 결제 대기·실패·취소·확인 필요 후원은 03의 흐름을 거치지 않고 바로 넣는다. */
    private fun insertDonation(recordingId: Long, status: String, amount: Long) {
        jdbcTemplate.update(
            "INSERT INTO donation (order_id, broadcast_type, broadcast_id, donor_id, streamer_id, amount, status, created_at) " +
                "VALUES (?, 'RECORDING', ?, (SELECT min(id) FROM member WHERE login_id <> 'streamer'), " +
                "(SELECT id FROM member WHERE login_id = 'streamer'), ?, ?, now())",
            UUID.randomUUID().toString(), recordingId, amount, status,
        )
    }

    private fun registerRecording(): Long =
        mockMvc.multipart("/api/recordings") {
            session = streamer
            with(csrf())
            param("title", "좋은 방송")
            param("url", "https://cdn.example.com/video.mp4")
        }.andExpect { status { isCreated() } }
            .andReturn().response.getHeader("Location")!!.substringAfterLast('/').toLong()
}
