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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post

@Import(TestcontainersConfiguration::class, FakePaymentGatewayConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class DonationApiTest {

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
    fun `후원을 시작하면 결제 대기 주문을 만들고 결제창에 넘길 주문번호와 금액을 준다`() {
        val recordingId = registerRecording("좋은 방송")

        mockMvc.post("/api/recordings/$recordingId/donations") {
            session = donor
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":5000}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.orderId") { isString() }
            jsonPath("$.amount") { value(5000) }
            jsonPath("$.orderName") { value("좋은 방송 후원") }
            jsonPath("$.streamer.nickname") { value("방송자") }
            jsonPath("$.customerKey") { isString() }
        }

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM donation", String::class.java))
            .isEqualTo("AWAITING_PAYMENT")
    }

    @Test
    fun `결제 후 승인을 확인하면 후원이 확정되고 그 방송의 내역과 합계에 나온다`() {
        val recordingId = registerRecording("좋은 방송")
        val orderId = startDonation("/api/recordings/$recordingId/donations", 5000)

        mockMvc.post("/api/donations/confirm") {
            session = donor
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"paymentKey":"pay_1","orderId":"$orderId","amount":5000}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
            jsonPath("$.amount") { value(5000) }
            jsonPath("$.streamer.nickname") { value("방송자") }
            jsonPath("$.broadcast.type") { value("RECORDING") }
            jsonPath("$.broadcast.id") { value(recordingId) }
            jsonPath("$.confirmedAt") { isString() }
        }

        mockMvc.get("/api/recordings/$recordingId/donations").andExpect {
            status { isOk() }
            jsonPath("$.totalAmount") { value(5000) }
            jsonPath("$.count") { value(1) }
            jsonPath("$.items[0].donor.nickname") { value("후원자") }
            jsonPath("$.items[0].amount") { value(5000) }
            jsonPath("$.items[0].confirmedAt") { isString() }
        }
        assertThat(gateway.confirmCalls).containsExactly(Triple("pay_1", orderId, 5000L))
        assertThat(jdbcTemplate.queryForList("SELECT source || ':' || result FROM payment_notification", String::class.java))
            .containsExactly("CONFIRM_REQUEST:NEW_CONFIRMED")
    }

    @Test
    fun `라이브 방송에도 후원을 시작할 수 있다`() {
        val live = mockMvc.startLiveAs(streamer, "라이브")

        mockMvc.post("/api/live-broadcasts/${live.id}/donations") {
            session = donor
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.streamer.nickname") { value("방송자") }
            jsonPath("$.orderName") { value("라이브 후원") }
        }
    }

    @Test
    fun `결제 설정은 결제창을 열 클라이언트 키를 준다`() {
        mockMvc.get("/api/payments/config").andExpect {
            status { isOk() }
            jsonPath("$.clientKey") { isString() }
        }
    }

    private fun registerRecording(title: String): Long =
        mockMvc.multipart("/api/recordings") {
            session = streamer
            with(csrf())
            param("title", title)
            param("url", "https://cdn.example.com/video.mp4")
        }.andExpect { status { isCreated() } }
            .andReturn().response.getHeader("Location")!!.substringAfterLast('/').toLong()

    private fun startDonation(path: String, amount: Int): String {
        val body = mockMvc.post(path) {
            session = donor
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":$amount}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return Regex("\"orderId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }
}
