package com.chzzz.donation

import com.chzzz.common.BroadcastType
import org.springframework.data.jpa.repository.EntityGraph
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface DonationRepository : JpaRepository<Donation, Long> {
    fun findByOrderId(orderId: String): Donation?

    /** 확정 규칙 1: 주문 행을 잠가(`SELECT … FOR UPDATE`) 같은 주문의 통지를 한 번에 하나씩 처리한다(ADR-0008). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Donation d WHERE d.orderId = :orderId")
    fun findForUpdateByOrderId(orderId: String): Donation?

    fun existsByPaymentKey(paymentKey: String): Boolean

    @EntityGraph(attributePaths = ["donor"])
    fun findAllByBroadcastTypeAndBroadcastIdAndStatusOrderByConfirmedAtDescIdDesc(
        broadcastType: BroadcastType,
        broadcastId: Long,
        status: DonationStatus,
    ): List<Donation>
}

interface PaymentNotificationRepository : JpaRepository<PaymentNotification, Long>
