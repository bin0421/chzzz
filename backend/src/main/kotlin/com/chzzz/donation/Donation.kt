package com.chzzz.donation

import com.chzzz.common.BroadcastType
import com.chzzz.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** 후원: 회원이 방송 하나를 보고 그 방송자에게 정한 금액을 보내는 것. 서버가 결제 승인을 확인해야 확정된다. */
@Entity
@Table(name = "donation")
class Donation private constructor(
    @Column(name = "order_id", nullable = false, length = 64)
    val orderId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "broadcast_type", nullable = false, length = 20)
    val broadcastType: BroadcastType,

    @Column(name = "broadcast_id", nullable = false)
    val broadcastId: Long,

    @ManyToOne
    @JoinColumn(name = "donor_id", nullable = false)
    val donor: Member,

    /** 후원을 시작한 순간의 방송 소유자. */
    @ManyToOne
    @JoinColumn(name = "streamer_id", nullable = false)
    val streamer: Member,

    @Column(nullable = false)
    val amount: Long,

    @Column(nullable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: DonationStatus = DonationStatus.AWAITING_PAYMENT
        protected set

    @Column(name = "payment_key", length = 200)
    var paymentKey: String? = null
        protected set

    @Column(name = "failure_code", length = 100)
    var failureCode: String? = null
        protected set

    @Column(name = "failure_message", length = 500)
    var failureMessage: String? = null
        protected set

    var confirmedAt: Instant? = null
        protected set

    /** 결제 서비스 승인이 확인되었다. */
    fun confirm(paymentKey: String, now: Instant) {
        status = DonationStatus.CONFIRMED
        this.paymentKey = paymentKey
        confirmedAt = now.truncatedTo(ChronoUnit.MICROS)
    }

    companion object {
        fun start(broadcastType: BroadcastType, broadcastId: Long, donor: Member, streamer: Member, amount: Long, now: Instant) =
            Donation(
                orderId = UUID.randomUUID().toString(),
                broadcastType = broadcastType,
                broadcastId = broadcastId,
                donor = donor,
                streamer = streamer,
                amount = amount,
                createdAt = now.truncatedTo(ChronoUnit.MICROS),
            )
    }
}

enum class DonationStatus {
    /** 결제 대기: 후원을 시작했지만 결제 승인이 아직 확인되지 않았다. */
    AWAITING_PAYMENT,

    /** 확정: 서버가 승인과 금액·방송자를 확인해 적립했다. */
    CONFIRMED,

    /** 실패: 결제가 거절되었거나 서버 확인을 통과하지 못했다. */
    FAILED,

    /** 취소: 후원자가 결제창에서 그만두었다. */
    CANCELED,

    /** 확인 필요: 결제 서비스 승인은 끝났는데 금액·주문번호가 맞지 않는 이상 상황. 적립하지 않는다. */
    NEEDS_REVIEW,
}
