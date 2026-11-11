package com.chzzz.donation

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.Base64

/**
 * 실제 토스 게이트웨이가 토스 문서의 모양대로 요청하고 응답을 읽는지, 이 PC에 띄운 가짜 HTTP 서버로 확인한다.
 * (승인: POST /v1/payments/confirm, 조회: GET /v1/payments/{paymentKey}, 인증: Basic base64("<시크릿 키>:"))
 */
class TossPaymentGatewayTest {

    private lateinit var server: HttpServer
    private val requests = mutableListOf<Triple<String, String, String>>() // 메서드·경로, Authorization, 본문
    private var nextStatus = 200
    private var nextBody = ""

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += Triple(
                "${exchange.requestMethod} ${exchange.requestURI.path}",
                exchange.requestHeaders.getFirst("Authorization").orEmpty(),
                exchange.requestBody.readAllBytes().decodeToString(),
            )
            val bytes = nextBody.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(nextStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun gateway(baseUrl: String = "http://127.0.0.1:${server.address.port}") =
        TossPaymentGateway(PaymentProperties(clientKey = "test_ck_x", secretKey = "test_sk_secret", apiBaseUrl = baseUrl))

    @Test
    fun `승인은 시크릿 키 Basic 인증으로 confirm에 결제 키·주문번호·금액을 보내고 완료 결제를 읽는다`() {
        nextBody = """{"paymentKey":"pay_1","orderId":"order-1","totalAmount":5000,"status":"DONE","method":"카드"}"""

        val result = gateway().confirm("pay_1", "order-1", 5000)

        assertThat(result).isEqualTo(GatewayResult.Payment("pay_1", "order-1", 5000, "DONE"))
        val (call, auth, body) = requests.single()
        assertThat(call).isEqualTo("POST /v1/payments/confirm")
        assertThat(auth).isEqualTo("Basic " + Base64.getEncoder().encodeToString("test_sk_secret:".toByteArray()))
        assertThat(body).contains("\"paymentKey\":\"pay_1\"", "\"orderId\":\"order-1\"", "\"amount\":5000")
    }

    @Test
    fun `결제 서비스가 거절하면 오류 코드와 메시지를 돌려준다`() {
        nextStatus = 400
        nextBody = """{"code":"ALREADY_PROCESSED_PAYMENT","message":"이미 처리된 결제 입니다."}"""

        val result = gateway().confirm("pay_1", "order-1", 5000)

        assertThat(result).isEqualTo(GatewayResult.Rejected("ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다."))
    }

    @Test
    fun `조회는 결제 키로 결제를 읽는다`() {
        nextBody = """{"paymentKey":"pay_1","orderId":"order-1","totalAmount":5000,"status":"DONE"}"""

        val result = gateway().find("pay_1")

        assertThat(result).isEqualTo(GatewayResult.Payment("pay_1", "order-1", 5000, "DONE"))
        assertThat(requests.single().first).isEqualTo("GET /v1/payments/pay_1")
    }

    @Test
    fun `결제 서비스와 통신하지 못하면 알 수 없음으로 돌려준다`() {
        server.stop(0)

        val result = gateway().confirm("pay_1", "order-1", 5000)

        assertThat(result).isInstanceOf(GatewayResult.Unavailable::class.java)
    }
}
