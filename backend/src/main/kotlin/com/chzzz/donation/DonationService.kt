package com.chzzz.donation

import com.chzzz.common.BroadcastType
import com.chzzz.common.NotFoundException
import com.chzzz.common.UnauthenticatedException
import com.chzzz.member.LoginMember
import com.chzzz.member.MemberRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class DonationService(
    private val donationRepository: DonationRepository,
    private val notificationRepository: PaymentNotificationRepository,
    private val memberRepository: MemberRepository,
    private val broadcasts: BroadcastDirectory,
    private val gateway: PaymentGateway,
    private val clock: Clock,
) {
    /** 결제 대기 후원을 만든다. 방송자는 이 순간의 방송 소유자다. */
    @Transactional
    fun start(type: BroadcastType, broadcastId: Long, amount: Long, member: LoginMember): StartedDonation {
        val broadcast = broadcasts.find(type, broadcastId) ?: throw NotFoundException("방송을 찾을 수 없습니다.")
        val donor = memberRepository.findByIdOrNull(member.id) ?: throw UnauthenticatedException()
        val streamer = broadcast.owner!!
        val donation = donationRepository.save(Donation.start(type, broadcastId, donor, streamer, amount, clock.instant()))
        return StartedDonation(donation, orderName = "${broadcast.title} 후원")
    }

    /** 결제 후 돌아온 화면의 확인 요청. 결제 서비스 승인을 받고 결과가 맞으면 확정한다. */
    @Transactional
    fun confirm(paymentKey: String, orderId: String, amount: Long, member: LoginMember): Donation {
        val donation = donationRepository.findByOrderId(orderId) ?: throw NotFoundException("후원을 찾을 수 없습니다.")
        val result = gateway.confirm(paymentKey, orderId, amount)
        if (result is GatewayResult.Payment && result.isDone && result.orderId == donation.orderId && result.totalAmount == donation.amount) {
            donation.confirm(paymentKey, clock.instant())
            record(paymentKey, orderId, NotificationSource.CONFIRM_REQUEST, NotificationResult.NEW_CONFIRMED, null)
        }
        return donation
    }

    @Transactional(readOnly = true)
    fun confirmedOf(type: BroadcastType, broadcastId: Long): List<Donation> =
        donationRepository.findAllByBroadcastTypeAndBroadcastIdAndStatusOrderByConfirmedAtDescIdDesc(
            type, broadcastId, DonationStatus.CONFIRMED,
        )

    private fun record(paymentKey: String?, orderId: String?, source: NotificationSource, result: NotificationResult, detail: String?) {
        notificationRepository.save(PaymentNotification(paymentKey, orderId, source, result, detail, clock.instant()))
    }
}

class StartedDonation(val donation: Donation, val orderName: String)
