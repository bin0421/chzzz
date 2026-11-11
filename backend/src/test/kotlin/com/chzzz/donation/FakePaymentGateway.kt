package com.chzzz.donation

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 테스트용 결제 게이트웨이. 토스 서버를 부르지 않고, 테스트마다 승인·조회 결과를 정하며 호출을 기록한다.
 * 기본값: 승인 요청은 요청한 주문번호·금액 그대로 완료(DONE)로 응답한다.
 */
class FakePaymentGateway : PaymentGateway {
    val confirmCalls = CopyOnWriteArrayList<Triple<String, String, Long>>()
    val findCalls = CopyOnWriteArrayList<String>()

    @Volatile
    var onConfirm: (paymentKey: String, orderId: String, amount: Long) -> GatewayResult =
        { paymentKey, orderId, amount -> GatewayResult.Payment(paymentKey, orderId, amount, "DONE") }

    @Volatile
    var onFind: (paymentKey: String) -> GatewayResult = { GatewayResult.Rejected("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다.") }

    override fun confirm(paymentKey: String, orderId: String, amount: Long): GatewayResult {
        confirmCalls += Triple(paymentKey, orderId, amount)
        return onConfirm(paymentKey, orderId, amount)
    }

    override fun find(paymentKey: String): GatewayResult {
        findCalls += paymentKey
        return onFind(paymentKey)
    }

    fun reset() {
        confirmCalls.clear()
        findCalls.clear()
        onConfirm = { paymentKey, orderId, amount -> GatewayResult.Payment(paymentKey, orderId, amount, "DONE") }
        onFind = { GatewayResult.Rejected("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다.") }
    }
}

/** 후원 테스트에서 `@Import`해 실제 토스 게이트웨이 대신 [FakePaymentGateway]를 쓴다. */
@TestConfiguration(proxyBeanMethods = false)
class FakePaymentGatewayConfiguration {
    @Bean
    @Primary
    fun fakePaymentGateway(): FakePaymentGateway = FakePaymentGateway()
}
