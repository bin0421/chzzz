package com.chzzz.donation

/**
 * 결제 서비스(토스페이먼츠) 경계. 후원 확정에 필요한 두 동작만 가진다(ADR-0007).
 * 실제 구현은 토스 REST API를 부르고, 테스트는 가짜 구현으로 바꿔 끼운다.
 */
interface PaymentGateway {
    /** 결제 승인을 요청한다. 이미 승인된 결제면 결제 서비스는 거절(`ALREADY_PROCESSED_PAYMENT`)을 준다. */
    fun confirm(paymentKey: String, orderId: String, amount: Long): GatewayResult

    /** 결제 키로 결제의 실제 상태를 조회한다. */
    fun find(paymentKey: String): GatewayResult
}

sealed interface GatewayResult {
    /** 결제 서비스가 돌려준 결제. [status]는 결제 서비스의 상태 값 그대로다(완료는 `DONE`). */
    data class Payment(val paymentKey: String, val orderId: String, val totalAmount: Long, val status: String) : GatewayResult {
        val isDone: Boolean get() = status == "DONE"
    }

    /** 결제 서비스가 요청을 거절했다(카드 거절, 이미 처리됨, 없는 결제 등). */
    data class Rejected(val code: String, val message: String) : GatewayResult

    /** 결제 서비스와 통신하지 못했다. 결과를 모르므로 다시 시도할 수 있게 둔다. */
    data class Unavailable(val reason: String) : GatewayResult
}
