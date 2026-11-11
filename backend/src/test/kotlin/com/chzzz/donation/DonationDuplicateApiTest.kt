package com.chzzz.donation

import com.chzzz.TestcontainersConfiguration
import com.chzzz.member.signUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.dao.DataIntegrityViolationException
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 같은 결제의 확인 요청이 여러 번 와도 후원은 한 번만 확정된다(ADR-0008). */
@Import(TestcontainersConfiguration::class, FakePaymentGatewayConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class DonationDuplicateApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var gateway: FakePaymentGateway

    lateinit var streamer: MockHttpSession
    lateinit var donor: MockHttpSession
    var recordingId: Long = 0
    val path get() = "/api/recordings/$recordingId/donations"

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
    fun `확정된 주문에 같은 확인 요청을 다시 보내면 승인을 다시 요청하지 않고 같은 결과를 돌려주며 이미 확정으로 기록한다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        val first = mockMvc.confirmDonation(donor, "pay_1", orderId, 5000)
            .andExpect { status { isOk() } }.andReturn().response.contentAsString

        val second = mockMvc.confirmDonation(donor, "pay_1", orderId, 5000)
            .andExpect { status { isOk() } }.andReturn().response.contentAsString

        assertThat(second).isEqualTo(first)
        assertThat(gateway.confirmCalls).hasSize(1)
        assertTotal(5000, 1)
        assertThat(results()).containsExactly("NEW_CONFIRMED", "ALREADY_CONFIRMED")
    }

    @Test
    fun `같은 확인 요청이 동시에 두 개 와도 한 건만 새로 확정되고 승인은 한 번만 요청한다`() {
        val orderId = mockMvc.startDonationAs(donor, path, 5000)
        // 승인 응답을 늦춰 두 요청이 겹치게 한다. 잠금이 없으면 둘 다 결제 대기를 보고 승인을 요청한다.
        gateway.onConfirm = { paymentKey, order, amount ->
            Thread.sleep(500)
            GatewayResult.Payment(paymentKey, order, amount, "DONE")
        }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val statuses = (1..2).map {
            pool.submit<Int> {
                start.await()
                mockMvc.confirmDonation(donor, "pay_1", orderId, 5000).andReturn().response.status
            }
        }
        start.countDown()
        val codes = statuses.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(codes).containsOnly(200)
        assertThat(gateway.confirmCalls).hasSize(1)
        assertTotal(5000, 1)
        assertThat(results()).containsExactlyInAnyOrder("NEW_CONFIRMED", "ALREADY_CONFIRMED")
    }

    @Test
    fun `다른 후원에 이미 붙은 결제 키로 확인하면 승인을 요청하지 않고 409이며 그 후원은 결제 대기 그대로다`() {
        val first = mockMvc.startDonationAs(donor, path, 5000)
        mockMvc.confirmDonation(donor, "pay_1", first, 5000).andExpect { status { isOk() } }
        val second = mockMvc.startDonationAs(donor, path, 5000)

        mockMvc.confirmDonation(donor, "pay_1", second, 5000).andExpect { status { isConflict() } }

        assertThat(gateway.confirmCalls).hasSize(1)
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM donation WHERE order_id = ?", String::class.java, second))
            .isEqualTo("AWAITING_PAYMENT")
        assertTotal(5000, 1)
        assertThat(results()).containsExactly("NEW_CONFIRMED", "REJECTED")
    }

    @Test
    fun `저장 구조에서도 같은 결제 키는 두 후원에 붙지 않는다`() {
        val first = mockMvc.startDonationAs(donor, path, 5000)
        val second = mockMvc.startDonationAs(donor, path, 5000)
        jdbcTemplate.update("UPDATE donation SET payment_key = 'pay_1' WHERE order_id = ?", first)

        assertThatThrownBy { jdbcTemplate.update("UPDATE donation SET payment_key = 'pay_1' WHERE order_id = ?", second) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun results(): List<String?> =
        jdbcTemplate.queryForList("SELECT result FROM payment_notification ORDER BY id", String::class.java)

    private fun assertTotal(total: Long, count: Int) {
        mockMvc.get(path).andExpect {
            jsonPath("$.totalAmount") { value(total) }
            jsonPath("$.count") { value(count) }
        }
    }
}
