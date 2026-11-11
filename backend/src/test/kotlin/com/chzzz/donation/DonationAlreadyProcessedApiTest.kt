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
 * 결제 서비스가 "이미 처리된 결제"라고 답하면 조회 API로 실제 상태를 확인해 같은 확정 규칙으로 처리한다(ADR-0008 3).
 * 승인은 끝났는데 서버가 확정하기 전에 끊긴 상황이다.
 */
@Import(TestcontainersConfiguration::class, FakePaymentGatewayConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class DonationAlreadyProcessedApiTest {

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
        gateway.onConfirm = { _, _, _ -> GatewayResult.Rejected("ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다.") }
    }

    @Test
    fun `이미 처리된 결제라는 답을 받으면 조회해서 완료이고 값이 같으면 확정한다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        gateway.onFind = { paymentKey -> GatewayResult.Payment(paymentKey, orderId, 5000, "DONE") }

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
        }

        assertThat(gateway.findCalls).containsExactly("pay_1")
        mockMvc.get(path).andExpect {
            jsonPath("$.totalAmount") { value(5000) }
            jsonPath("$.count") { value(1) }
        }
        assertThat(jdbcTemplate.queryForList("SELECT result FROM payment_notification", String::class.java))
            .containsExactly("NEW_CONFIRMED")
    }

    @Test
    fun `조회 결과의 금액이 다르면 적립하지 않고 확인 필요로 남긴다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        gateway.onFind = { paymentKey -> GatewayResult.Payment(paymentKey, orderId, 100, "DONE") }

        mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NEEDS_REVIEW") }
        }

        mockMvc.get(path).andExpect { jsonPath("$.count") { value(0) } }
    }
}
