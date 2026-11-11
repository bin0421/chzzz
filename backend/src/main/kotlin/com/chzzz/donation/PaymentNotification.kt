package com.chzzz.donation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 받은 승인 통지 한 건과 처리 결과(ADR-0008). 화면에는 나오지 않고, 중복 통지 처리를 보여 주는 근거로 남긴다. */
@Entity
@Table(name = "payment_notification")
class PaymentNotification(
    @Column(name = "payment_key", length = 200)
    val paymentKey: String?,

    @Column(name = "order_id", length = 64)
    val orderId: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val source: NotificationSource,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val result: NotificationResult,

    @Column(length = 500)
    val detail: String?,

    receivedAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant = receivedAt.truncatedTo(ChronoUnit.MICROS)
}

enum class NotificationSource { CONFIRM_REQUEST, WEBHOOK }

enum class NotificationResult { NEW_CONFIRMED, ALREADY_CONFIRMED, IGNORED, REJECTED }
