package com.chzzz.donation

import com.chzzz.common.FieldValidationException

/** 후원 금액 규칙: 원 단위 정수, 1,000 이상 1,000,000 이하. */
object DonationAmount {
    const val MIN = 1_000L
    const val MAX = 1_000_000L

    /**
     * 요청 본문의 값을 금액으로 바꾼다. 본문은 형식 그대로(정수·소수·문자열) 받아야
     * 소수를 정수로 잘라 버리거나 숫자 아님을 400 `errors` 없이 거절하는 일을 막을 수 있다.
     */
    fun parse(raw: Any?): Long {
        val amount = when (raw) {
            is Int -> raw.toLong()
            is Long -> raw
            else -> null
        } ?: throw invalid()
        if (amount !in MIN..MAX) throw invalid()
        return amount
    }

    private fun invalid() =
        FieldValidationException("amount", "후원 금액은 1,000원 이상 1,000,000원 이하의 정수로 입력해 주세요.")
}
