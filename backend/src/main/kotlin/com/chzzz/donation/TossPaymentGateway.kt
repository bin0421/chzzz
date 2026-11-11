package com.chzzz.donation

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.Base64

/** 토스페이먼츠 설정. 기본값은 토스 공식 샘플의 공개 테스트 키(API 개별 연동 키)다(ADR-0007). */
@ConfigurationProperties("payment.toss")
data class PaymentProperties(
    val clientKey: String,
    val secretKey: String,
    val apiBaseUrl: String,
)

/** 토스페이먼츠 REST API로 결제 승인·조회를 한다. 시크릿 키는 `"<키>:"`를 Base64로 인코딩해 Basic 인증에 쓴다. */
@Component
class TossPaymentGateway(properties: PaymentProperties) : PaymentGateway {
    private val client = RestClient.builder()
        .baseUrl(properties.apiBaseUrl)
        .defaultHeader(
            HttpHeaders.AUTHORIZATION,
            "Basic " + Base64.getEncoder().encodeToString("${properties.secretKey}:".toByteArray()),
        )
        .build()

    override fun confirm(paymentKey: String, orderId: String, amount: Long): GatewayResult = call {
        client.post().uri("/v1/payments/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("paymentKey" to paymentKey, "orderId" to orderId, "amount" to amount))
            .exchange { _, response -> read(response.statusCode, response.bodyTo(Map::class.java)) }
    }

    override fun find(paymentKey: String): GatewayResult = call {
        client.get().uri("/v1/payments/{paymentKey}", paymentKey)
            .exchange { _, response -> read(response.statusCode, response.bodyTo(Map::class.java)) }
    }

    private fun read(status: HttpStatusCode, body: Map<*, *>?): GatewayResult {
        if (!status.is2xxSuccessful || body == null) {
            return GatewayResult.Rejected(body?.get("code")?.toString() ?: "HTTP_${status.value()}", body?.get("message")?.toString() ?: "")
        }
        return GatewayResult.Payment(
            paymentKey = body["paymentKey"].toString(),
            orderId = body["orderId"].toString(),
            totalAmount = (body["totalAmount"] as Number).toLong(),
            status = body["status"].toString(),
        )
    }

    private fun call(block: () -> GatewayResult): GatewayResult = try {
        block()
    } catch (e: RestClientException) {
        GatewayResult.Unavailable(e.message ?: "결제 서비스와 통신하지 못했습니다.")
    }
}

@org.springframework.context.annotation.Configuration
@org.springframework.boot.context.properties.EnableConfigurationProperties(PaymentProperties::class)
class PaymentConfiguration
