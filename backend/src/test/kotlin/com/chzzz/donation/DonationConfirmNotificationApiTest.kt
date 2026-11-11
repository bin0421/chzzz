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

/** 확인 요청마다 통지 기록에 처리 결과(새로 확정·거절)가 한 줄씩 남는다(ADR-0008). */
@Import(TestcontainersConfiguration::class, FakePaymentGatewayConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class DonationConfirmNotificationApiTest {

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
    fun `거절된 확인 요청도 이유와 함께 거절로 기록되고 확정된 요청은 새로 확정으로 기록된다`() {
        val path = "/api/recordings/$recordingId/donations"
        val other = mockMvc.signUp("other", "다른회원")

        val forged = mockMvc.startDonationAs(donor, path, 5000)
        mockMvc.confirmDonation(donor, "pay_forged", forged, 1000).andExpect { status { isBadRequest() } }

        val someoneElses = mockMvc.startDonationAs(donor, path, 5000)
        mockMvc.confirmDonation(other, "pay_other", someoneElses, 5000).andExpect { status { isForbidden() } }

        val rejected = mockMvc.startDonationAs(donor, path, 5000)
        gateway.onConfirm = { _, _, _ -> GatewayResult.Rejected("REJECT_CARD_PAYMENT", "카드 거절") }
        mockMvc.confirmDonation(donor, "pay_rejected", rejected, 5000).andExpect { status { isOk() } }

        val retried = mockMvc.startDonationAs(donor, path, 5000)
        gateway.onConfirm = { _, _, _ -> GatewayResult.Unavailable("connect timed out") }
        mockMvc.confirmDonation(donor, "pay_retried", retried, 5000).andExpect { status { isBadGateway() } }
        gateway.reset()
        mockMvc.confirmDonation(donor, "pay_retried", retried, 5000).andExpect { status { isOk() } }

        val records = jdbcTemplate.queryForList(
            "SELECT payment_key, order_id, source, result, detail FROM payment_notification ORDER BY id",
        )
        assertThat(records.map { "${it["payment_key"]}:${it["source"]}:${it["result"]}" }).containsExactly(
            "pay_forged:CONFIRM_REQUEST:REJECTED",
            "pay_other:CONFIRM_REQUEST:REJECTED",
            "pay_rejected:CONFIRM_REQUEST:REJECTED",
            "pay_retried:CONFIRM_REQUEST:REJECTED",
            "pay_retried:CONFIRM_REQUEST:NEW_CONFIRMED",
        )
        assertThat(records.map { it["order_id"] }).containsExactly(forged, someoneElses, rejected, retried, retried)
        assertThat(records.take(4).map { it["detail"] as String? }).allSatisfy { assertThat(it).isNotBlank() }
    }
}
