package com.chzzz.donation

import com.chzzz.TestcontainersConfiguration
import com.chzzz.member.signUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get

/** 확인 요청 검증 규칙(스펙 "확정 규칙" 2·4·5): 결제 서비스를 부르기 전에 서버가 거절하는 경우. */
@Import(TestcontainersConfiguration::class, FakePaymentGatewayConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class DonationConfirmRulesApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var gateway: FakePaymentGateway

    lateinit var streamer: MockHttpSession
    lateinit var donor: MockHttpSession

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE donation, payment_notification RESTART IDENTITY")
        gateway.reset()
        streamer = mockMvc.signUp("streamer", "방송자")
        donor = mockMvc.signUp("donor", "후원자")
    }

    @Test
    fun `확인 요청 금액이 후원 금액과 다르면 승인을 요청하지 않고 400으로 거절하며 후원은 실패한다`() {
        val recordingId = mockMvc.registerRecordingAs(streamer)
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)

        mockMvc.confirmDonation(donor, "pay_1", orderId, 1000).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("amount") }
        }

        assertThat(gateway.confirmCalls).isEmpty()
        assertThat(statusOf(orderId)).isEqualTo("FAILED")
        mockMvc.get("/api/recordings/$recordingId/donations").andExpect {
            jsonPath("$.totalAmount") { value(0) }
            jsonPath("$.count") { value(0) }
        }
    }

    @Test
    fun `다른 회원의 주문을 확인하려 하면 403이고 후원은 결제 대기 그대로다`() {
        val recordingId = mockMvc.registerRecordingAs(streamer)
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)
        val other = mockMvc.signUp("other", "다른회원")

        mockMvc.confirmDonation(other, "pay_1", orderId, 5000).andExpect { status { isForbidden() } }

        assertThat(gateway.confirmCalls).isEmpty()
        assertThat(statusOf(orderId)).isEqualTo("AWAITING_PAYMENT")
    }

    @Test
    fun `비로그인 확인 요청은 401, 없는 주문은 404다`() {
        val recordingId = mockMvc.registerRecordingAs(streamer)
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)

        mockMvc.confirmDonation(null, "pay_1", orderId, 5000).andExpect { status { isUnauthorized() } }
        mockMvc.confirmDonation(donor, "pay_1", "no-such-order", 5000).andExpect { status { isNotFound() } }

        assertThat(gateway.confirmCalls).isEmpty()
        assertThat(statusOf(orderId)).isEqualTo("AWAITING_PAYMENT")
    }

    @Test
    fun `후원을 시작한 뒤 방송이 삭제되었으면 승인을 요청하지 않고 409로 거절하며 후원은 실패한다`() {
        val recordingId = mockMvc.registerRecordingAs(streamer)
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)
        mockMvc.delete("/api/recordings/$recordingId") {
            session = streamer
            with(csrf())
        }.andExpect { status { isNoContent() } }

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect { status { isConflict() } }

        assertThat(gateway.confirmCalls).isEmpty()
        assertThat(statusOf(orderId)).isEqualTo("FAILED")
    }

    @Test
    fun `후원을 시작한 뒤 방송 소유자가 바뀌었으면 승인을 요청하지 않고 409로 거절하며 후원은 실패한다`() {
        val recordingId = mockMvc.registerRecordingAs(streamer)
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)
        mockMvc.signUp("newowner", "새소유자")
        // 소유자를 바꾸는 API는 없다. 저장된 방송자와 현재 소유자가 다른 상황을 DB로 만든다.
        jdbcTemplate.update(
            "UPDATE recording SET owner_id = (SELECT id FROM member WHERE login_id = 'newowner') WHERE id = ?",
            recordingId,
        )

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect { status { isConflict() } }

        assertThat(gateway.confirmCalls).isEmpty()
        assertThat(statusOf(orderId)).isEqualTo("FAILED")
    }

    @Test
    fun `이미 실패한 주문은 올바른 금액으로 다시 확인해도 409이고 승인을 요청하지 않는다`() {
        val recordingId = mockMvc.registerRecordingAs(streamer)
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)
        mockMvc.confirmDonation(donor, "pay_1", orderId, 1000).andExpect { status { isBadRequest() } }

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect { status { isConflict() } }

        assertThat(gateway.confirmCalls).isEmpty()
        assertThat(statusOf(orderId)).isEqualTo("FAILED")
    }

    @Test
    fun `취소된 주문과 확인 필요 주문도 다시 확인하면 409이고 승인을 요청하지 않는다`() {
        val path = "/api/recordings/${mockMvc.registerRecordingAs(streamer)}/donations"
        val canceled = mockMvc.startDonationAs(donor, path, 5000)
        mockMvc.reportDonationFailure(donor, canceled, "PAY_PROCESS_CANCELED", "취소").andExpect { status { isOk() } }
        val needsReview = mockMvc.startDonationAs(donor, path, 5000)
        gateway.onConfirm = { paymentKey, orderId, _ -> GatewayResult.Payment(paymentKey, orderId, 1, "DONE") }
        mockMvc.confirmDonation(donor, "pay_review", needsReview, 5000).andExpect { status { isOk() } }
        gateway.reset()

        mockMvc.confirmDonation(donor, "pay_1", canceled, 5000).andExpect { status { isConflict() } }
        mockMvc.confirmDonation(donor, "pay_review", needsReview, 5000).andExpect { status { isConflict() } }

        assertThat(gateway.confirmCalls).isEmpty()
        assertThat(statusOf(canceled)).isEqualTo("CANCELED")
        assertThat(statusOf(needsReview)).isEqualTo("NEEDS_REVIEW")
    }

    private fun statusOf(orderId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM donation WHERE order_id = ?", String::class.java, orderId)!!
}
