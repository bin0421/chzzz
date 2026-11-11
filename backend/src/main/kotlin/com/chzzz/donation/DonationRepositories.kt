package com.chzzz.donation

import com.chzzz.common.BroadcastType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface DonationRepository : JpaRepository<Donation, Long> {
    fun findByOrderId(orderId: String): Donation?

    @EntityGraph(attributePaths = ["donor"])
    fun findAllByBroadcastTypeAndBroadcastIdAndStatusOrderByConfirmedAtDescIdDesc(
        broadcastType: BroadcastType,
        broadcastId: Long,
        status: DonationStatus,
    ): List<Donation>
}

interface PaymentNotificationRepository : JpaRepository<PaymentNotification, Long>
