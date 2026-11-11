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

/**
 * 토스 결제 상태 변경 웹훅(`POST /api/payments/toss/webhook`). 본문은 결제 키를 얻는 데만 쓰고
 * 조회 API로 실제 상태를 확인해 확인 요청과 같은 확정 규칙으로 처리한다. 항상 200이고 결과는 통지 기록에 남는다(ADR-0008).
 */
@Import(TestcontainersConfiguration::class, FakePaymentGatewayConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class DonationWebhookApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var gateway: FakePaymentGateway

    lateinit var donor: MockHttpSession
    var recordingId: Long = 0
    val path get() = "/api/recordings/$recordingId/donations"

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE donation, payment_notification RESTART IDENTITY")
        gateway.reset()
        val streamer = mockMvc.signUp("streamer", "방송자")
        donor = mockMvc.signUp("donor", "후원자")
        recordingId = mockMvc.registerRecordingAs(streamer)
    }

    @Test
    fun `확인 요청 없이 웹훅만 와도 조회 결과가 완료이고 값이 같으면 확정한다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        paymentIs(orderId, 5000, "DONE")

        mockMvc.sendTossWebhook("pay_1", orderId).andExpect { status { isOk() } }

        assertThat(gateway.findCalls).containsExactly("pay_1")
        assertThat(gateway.confirmCalls).isEmpty()
        assertThat(statusOf(orderId)).isEqualTo("CONFIRMED")
        assertTotal(5000, 1)
        assertThat(records()).containsExactly("pay_1:WEBHOOK:NEW_CONFIRMED")
    }

    @Test
    fun `같은 웹훅이 두 번 오면 한 번만 적립하고 통지 기록은 두 줄이다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        paymentIs(orderId, 5000, "DONE")

        mockMvc.sendTossWebhook("pay_1", orderId).andExpect { status { isOk() } }
        mockMvc.sendTossWebhook("pay_1", orderId).andExpect { status { isOk() } }

        assertTotal(5000, 1)
        assertThat(records()).containsExactly("pay_1:WEBHOOK:NEW_CONFIRMED", "pay_1:WEBHOOK:ALREADY_CONFIRMED")
    }

    @Test
    fun `확인 요청 뒤에 온 웹훅은 이미 확정으로 끝난다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        paymentIs(orderId, 5000, "DONE")
        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect { status { isOk() } }

        mockMvc.sendTossWebhook("pay_1", orderId).andExpect { status { isOk() } }

        assertTotal(5000, 1)
        assertThat(records()).containsExactly("pay_1:CONFIRM_REQUEST:NEW_CONFIRMED", "pay_1:WEBHOOK:ALREADY_CONFIRMED")
    }

    @Test
    fun `웹훅 뒤에 온 확인 요청은 승인을 요청하지 않고 이미 확정으로 같은 결과를 돌려준다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        paymentIs(orderId, 5000, "DONE")
        mockMvc.sendTossWebhook("pay_1", orderId).andExpect { status { isOk() } }

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
        }

        assertThat(gateway.confirmCalls).isEmpty()
        assertTotal(5000, 1)
        assertThat(records()).containsExactly("pay_1:WEBHOOK:NEW_CONFIRMED", "pay_1:CONFIRM_REQUEST:ALREADY_CONFIRMED")
    }

    @Test
    fun `조회 결과가 완료가 아니면 위조로 보고 적립하지 않고 무시로 기록한다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        paymentIs(orderId, 5000, "WAITING_FOR_DEPOSIT")

        mockMvc.sendTossWebhook("pay_1", orderId, status = "DONE").andExpect { status { isOk() } }

        assertNotCredited(orderId)
        assertThat(records()).containsExactly("pay_1:WEBHOOK:IGNORED")
    }

    @Test
    fun `본문의 주문번호가 조회 결과와 다르면 적립하지 않고 무시로 기록한다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        val other = mockMvc.startDonationAs(donor, path, 5000)
        paymentIs(other, 5000, "DONE")

        mockMvc.sendTossWebhook("pay_1", orderId).andExpect { status { isOk() } }

        assertNotCredited(orderId)
        assertNotCredited(other)
        assertThat(records()).containsExactly("pay_1:WEBHOOK:IGNORED")
    }

    @Test
    fun `조회 결과의 금액이 후원 금액과 다르면 적립하지 않고 무시로 기록한다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        paymentIs(orderId, 1000, "DONE")

        mockMvc.sendTossWebhook("pay_1", orderId).andExpect { status { isOk() } }

        assertNotCredited(orderId)
        assertThat(records()).containsExactly("pay_1:WEBHOOK:IGNORED")
    }

    @Test
    fun `결제 서비스에 없는 결제 키면 적립하지 않고 무시로 기록한다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)

        mockMvc.sendTossWebhook("pay_forged", orderId).andExpect { status { isOk() } }

        assertNotCredited(orderId)
        assertThat(records()).containsExactly("pay_forged:WEBHOOK:IGNORED")
    }

    @Test
    fun `결제 상태 변경이 아닌 이벤트는 조회하지 않고 무시로 기록한다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        paymentIs(orderId, 5000, "DONE")

        mockMvc.sendTossWebhook("pay_1", orderId, eventType = "DEPOSIT_CALLBACK").andExpect { status { isOk() } }

        assertThat(gateway.findCalls).isEmpty()
        assertNotCredited(orderId)
        assertThat(records()).containsExactly("pay_1:WEBHOOK:IGNORED")
    }

    @Test
    fun `실패하거나 취소된 주문에 온 웹훅은 확정하지 않는다`() {
        val failed = mockMvc.startDonationAs(donor, path, 5000)
        mockMvc.reportDonationFailure(donor, failed, "REJECT_CARD_COMPANY", "거절").andExpect { status { isOk() } }
        val canceled = mockMvc.startDonationAs(donor, path, 5000)
        mockMvc.reportDonationFailure(donor, canceled, "PAY_PROCESS_CANCELED", "취소").andExpect { status { isOk() } }
        gateway.onFind = { key -> GatewayResult.Payment(key, if (key == "pay_f") failed else canceled, 5000, "DONE") }

        mockMvc.sendTossWebhook("pay_f", failed).andExpect { status { isOk() } }
        mockMvc.sendTossWebhook("pay_c", canceled).andExpect { status { isOk() } }

        assertThat(statusOf(failed)).isEqualTo("FAILED")
        assertThat(statusOf(canceled)).isEqualTo("CANCELED")
        assertTotal(0, 0)
        assertThat(records()).containsExactly("pay_f:WEBHOOK:REJECTED", "pay_c:WEBHOOK:REJECTED")
    }

    @Test
    fun `결제 서비스에 조회하지 못하면 적립하지 않고 결제 대기로 남겨 다음 통지에서 확정한다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        gateway.onFind = { GatewayResult.Unavailable("connect timed out") }

        mockMvc.sendTossWebhook("pay_1", orderId).andExpect { status { isOk() } }
        assertNotCredited(orderId)

        paymentIs(orderId, 5000, "DONE")
        mockMvc.sendTossWebhook("pay_1", orderId).andExpect { status { isOk() } }

        assertTotal(5000, 1)
        assertThat(records()).containsExactly("pay_1:WEBHOOK:REJECTED", "pay_1:WEBHOOK:NEW_CONFIRMED")
    }

    private fun paymentIs(orderId: String, amount: Long, status: String) {
        gateway.onFind = { key -> GatewayResult.Payment(key, orderId, amount, status) }
    }

    private fun statusOf(orderId: String): String? =
        jdbcTemplate.queryForObject("SELECT status FROM donation WHERE order_id = ?", String::class.java, orderId)

    private fun assertNotCredited(orderId: String) {
        assertThat(statusOf(orderId)).isEqualTo("AWAITING_PAYMENT")
        assertTotal(0, 0)
    }

    private fun records(): List<String> =
        jdbcTemplate.queryForList("SELECT payment_key, source, result FROM payment_notification ORDER BY id")
            .map { "${it["payment_key"]}:${it["source"]}:${it["result"]}" }

    private fun assertTotal(total: Long, count: Int) {
        mockMvc.get(path).andExpect {
            jsonPath("$.totalAmount") { value(total) }
            jsonPath("$.count") { value(count) }
        }
    }
}
