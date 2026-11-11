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

/** 결제창에서 실패·취소하고 돌아온 화면의 실패 보고(`POST /api/donations/fail`). */
@Import(TestcontainersConfiguration::class, FakePaymentGatewayConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class DonationFailReportApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var gateway: FakePaymentGateway

    lateinit var streamer: MockHttpSession
    lateinit var donor: MockHttpSession
    var recordingId: Long = 0
    lateinit var path: String

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE donation, payment_notification RESTART IDENTITY")
        gateway.reset()
        streamer = mockMvc.signUp("streamer", "방송자")
        donor = mockMvc.signUp("donor", "후원자")
        recordingId = mockMvc.registerRecordingAs(streamer)
        path = "/api/recordings/$recordingId/donations"
    }

    @Test
    fun `결제창에서 사용자가 취소했다고 보고하면 후원은 취소되고 방송을 알려 준다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)

        mockMvc.reportDonationFailure(donor, orderId, "PAY_PROCESS_CANCELED", "결제가 사용자에 의해 취소되었습니다.").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CANCELED") }
            jsonPath("$.broadcast.type") { value("RECORDING") }
            jsonPath("$.broadcast.id") { value(recordingId) }
        }

        assertThat(statusOf(orderId)).isEqualTo("CANCELED")
        assertNoDonationsListed()
    }

    @Test
    fun `취소가 아닌 오류 코드로 보고하면 후원은 실패하고 코드와 메시지가 남는다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)

        mockMvc.reportDonationFailure(donor, orderId, "REJECT_CARD_COMPANY", "카드사에서 거절했습니다.").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("FAILED") }
        }

        assertThat(jdbcTemplate.queryForMap("SELECT status, failure_code, failure_message FROM donation WHERE order_id = ?", orderId))
            .containsEntry("status", "FAILED")
            .containsEntry("failure_code", "REJECT_CARD_COMPANY")
            .containsEntry("failure_message", "카드사에서 거절했습니다.")
        assertNoDonationsListed()
    }

    @Test
    fun `이미 확정된 후원은 실패 보고로 바뀌지 않고 409다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect { status { isOk() } }

        mockMvc.reportDonationFailure(donor, orderId, "PAY_PROCESS_CANCELED", "취소").andExpect { status { isConflict() } }

        assertThat(statusOf(orderId)).isEqualTo("CONFIRMED")
        mockMvc.get(path).andExpect { jsonPath("$.totalAmount") { value(5000) } }
    }

    @Test
    fun `다른 회원의 주문은 403, 비로그인은 401이고 후원은 결제 대기 그대로다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        val other = mockMvc.signUp("other", "다른회원")

        mockMvc.reportDonationFailure(other, orderId, "PAY_PROCESS_CANCELED", "취소").andExpect { status { isForbidden() } }
        mockMvc.reportDonationFailure(null, orderId, "PAY_PROCESS_CANCELED", "취소").andExpect { status { isUnauthorized() } }

        assertThat(statusOf(orderId)).isEqualTo("AWAITING_PAYMENT")
    }

    private fun statusOf(orderId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM donation WHERE order_id = ?", String::class.java, orderId)!!

    private fun assertNoDonationsListed() {
        mockMvc.get(path).andExpect {
            jsonPath("$.totalAmount") { value(0) }
            jsonPath("$.count") { value(0) }
        }
    }
}
