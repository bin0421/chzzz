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
import org.springframework.test.web.servlet.get

/** 승인 요청 뒤 결제 서비스 결과에 따른 처리(스펙 "확정 규칙" 7): 거절, 값 불일치, 통신 실패. */
@Import(TestcontainersConfiguration::class, FakePaymentGatewayConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class DonationConfirmOutcomeApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var gateway: FakePaymentGateway

    lateinit var streamer: MockHttpSession
    lateinit var donor: MockHttpSession
    var recordingId: Long = 0

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE donation, payment_notification RESTART IDENTITY")
        gateway.reset()
        streamer = mockMvc.signUp("streamer", "방송자")
        donor = mockMvc.signUp("donor", "후원자")
        recordingId = mockMvc.registerRecordingAs(streamer)
    }

    @Test
    fun `결제 서비스가 승인을 거절하면 후원은 실패하고 응답과 저장에 거절 이유가 남는다`() {
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)
        gateway.onConfirm = { _, _, _ -> GatewayResult.Rejected("REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다.") }

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.failure.code") { value("REJECT_CARD_PAYMENT") }
            jsonPath("$.failure.message") { value("한도초과 혹은 잔액부족으로 결제에 실패했습니다.") }
            jsonPath("$.confirmedAt") { doesNotExist() }
        }

        assertThat(jdbcTemplate.queryForMap("SELECT status, failure_code FROM donation WHERE order_id = ?", orderId))
            .containsEntry("status", "FAILED")
            .containsEntry("failure_code", "REJECT_CARD_PAYMENT")
        assertNoDonationsListed()
    }

    @Test
    fun `승인은 완료인데 승인 금액이 다르면 적립하지 않고 확인 필요로 남긴다`() {
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)
        gateway.onConfirm = { paymentKey, order, _ -> GatewayResult.Payment(paymentKey, order, 50, "DONE") }

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NEEDS_REVIEW") }
            jsonPath("$.failure.message") { isNotEmpty() }
        }

        assertThat(statusOf(orderId)).isEqualTo("NEEDS_REVIEW")
        assertNoDonationsListed()
    }

    @Test
    fun `승인은 완료인데 주문번호가 다르면 적립하지 않고 확인 필요로 남긴다`() {
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)
        gateway.onConfirm = { paymentKey, _, amount -> GatewayResult.Payment(paymentKey, "other-order", amount, "DONE") }

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NEEDS_REVIEW") }
        }

        assertThat(statusOf(orderId)).isEqualTo("NEEDS_REVIEW")
        assertNoDonationsListed()
    }

    @Test
    fun `승인 응답의 결제 상태가 완료가 아니면 적립하지 않고 실패로 남긴다`() {
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)
        gateway.onConfirm = { paymentKey, order, amount -> GatewayResult.Payment(paymentKey, order, amount, "ABORTED") }

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.failure.message") { isNotEmpty() }
        }

        assertNoDonationsListed()
    }

    @Test
    fun `결제 서비스와 통신하지 못하면 502이고 결제 대기로 남아 같은 요청을 다시 보내면 확정된다`() {
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)
        gateway.onConfirm = { _, _, _ -> GatewayResult.Unavailable("connect timed out") }

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect {
            status { isBadGateway() }
            jsonPath("$.detail") { isNotEmpty() }
        }
        assertThat(statusOf(orderId)).isEqualTo("AWAITING_PAYMENT")

        gateway.onConfirm = { paymentKey, order, amount -> GatewayResult.Payment(paymentKey, order, amount, "DONE") }
        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
        }
        mockMvc.get("/api/recordings/$recordingId/donations").andExpect {
            jsonPath("$.totalAmount") { value(5000) }
            jsonPath("$.count") { value(1) }
        }
    }

    @Test
    fun `확정된 후원은 다시 확인한 요청을 결제 서비스가 거절해도 확정 그대로다`() {
        val orderId = mockMvc.startDonationAs(donor, "/api/recordings/$recordingId/donations", 5000)
        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect { status { isOk() } }
        gateway.onConfirm = { _, _, _ -> GatewayResult.Rejected("ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다.") }

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
        }

        assertThat(statusOf(orderId)).isEqualTo("CONFIRMED")
        mockMvc.get("/api/recordings/$recordingId/donations").andExpect {
            jsonPath("$.totalAmount") { value(5000) }
            jsonPath("$.count") { value(1) }
        }
        assertThat(jdbcTemplate.queryForList("SELECT result FROM payment_notification ORDER BY id", String::class.java))
            .containsExactly("NEW_CONFIRMED", "REJECTED")
    }

    private fun statusOf(orderId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM donation WHERE order_id = ?", String::class.java, orderId)!!

    private fun assertNoDonationsListed() {
        mockMvc.get("/api/recordings/$recordingId/donations").andExpect {
            jsonPath("$.totalAmount") { value(0) }
            jsonPath("$.count") { value(0) }
        }
    }
}
