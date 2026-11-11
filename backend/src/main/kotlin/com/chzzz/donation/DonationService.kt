package com.chzzz.donation

import com.chzzz.common.BadGatewayException
import com.chzzz.common.BroadcastType
import com.chzzz.common.ConflictException
import com.chzzz.common.FieldValidationException
import com.chzzz.common.ForbiddenException
import com.chzzz.common.NotFoundException
import com.chzzz.common.Ownership
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

    /**
     * 결제 후 돌아온 화면의 확인 요청(스펙 "확정 규칙"). 주문·후원자·금액·방송자를 먼저 확인하고, 맞을 때만 결제 서비스 승인을 받아
     * 응답이 완료이며 값이 같으면 확정한다. 거절해도 후원 상태 변경과 통지 기록은 남아야 하므로 거절 예외에는 되돌리지 않는다.
     */
    @Transactional(noRollbackFor = [NotFoundException::class, ForbiddenException::class, ConflictException::class, FieldValidationException::class, BadGatewayException::class])
    fun confirm(paymentKey: String, orderId: String, amount: Long, member: LoginMember): Donation {
        val outcome = try {
            approve(paymentKey, orderId, amount, member)
        } catch (e: RuntimeException) {
            if (e.isRejection()) record(paymentKey, orderId, NotificationSource.CONFIRM_REQUEST, NotificationResult.REJECTED, e.message)
            throw e
        }
        if (outcome.newlyConfirmed) {
            record(paymentKey, orderId, NotificationSource.CONFIRM_REQUEST, NotificationResult.NEW_CONFIRMED, null)
        } else {
            record(paymentKey, orderId, NotificationSource.CONFIRM_REQUEST, NotificationResult.REJECTED, outcome.reason)
        }
        return outcome.donation
    }

    private fun approve(paymentKey: String, orderId: String, amount: Long, member: LoginMember): ConfirmOutcome {
        val donation = donationRepository.findByOrderId(orderId) ?: throw NotFoundException("후원을 찾을 수 없습니다.")
        Ownership.requireOwner(donation.donor.id, member, "자기 후원만 확인할 수 있습니다.")
        if (donation.status in CLOSED) throw ConflictException("이미 실패했거나 취소된 후원입니다.")
        if (amount != donation.amount) {
            donation.fail("AMOUNT_MISMATCH", "결제 금액이 후원 금액과 다릅니다.")
            throw FieldValidationException("amount", "결제 금액이 후원 금액과 다릅니다.")
        }
        val owner = broadcasts.find(donation.broadcastType, donation.broadcastId)?.owner
        if (owner?.id != donation.streamer.id) {
            donation.fail("STREAMER_CHANGED", "방송이 사라졌거나 방송자가 바뀌어 후원할 수 없습니다.")
            throw ConflictException("방송이 사라졌거나 방송자가 바뀌어 후원할 수 없습니다.")
        }
        // 이미 확정된 후원을 다시 확인해도 상태는 바뀌지 않는다(Donation이 지킨다). 결제 서비스를 다시 부르지 않는 처리는 04에서 한다.
        val reason = when (val result = gateway.confirm(paymentKey, orderId, amount)) {
            is GatewayResult.Payment -> when {
                !result.isDone -> "결제가 완료되지 않았습니다(결제 상태 ${result.status})."
                    .also { donation.fail("PAYMENT_NOT_DONE", it) }
                result.orderId != donation.orderId || result.totalAmount != donation.amount ->
                    "결제 서비스의 승인 내용(주문 ${result.orderId}, ${result.totalAmount}원)이 후원과 맞지 않아 확인이 필요합니다."
                        .also { donation.needsReview("PAYMENT_MISMATCH", it) }
                donation.status == DonationStatus.AWAITING_PAYMENT -> {
                    donation.confirm(paymentKey, clock.instant())
                    return ConfirmOutcome(donation, newlyConfirmed = true)
                }
                else -> "이미 확정된 후원입니다."
            }
            is GatewayResult.Rejected -> result.message.also { donation.fail(result.code, it) }
            // 결과를 모르므로 결제 대기로 두고, 같은 요청을 다시 보내면 다시 확인한다.
            is GatewayResult.Unavailable -> throw BadGatewayException("결제 서비스와 연결하지 못했습니다. 잠시 후 다시 확인해 주세요.")
        }
        return ConfirmOutcome(donation, newlyConfirmed = false, reason = reason)
    }

    private class ConfirmOutcome(val donation: Donation, val newlyConfirmed: Boolean, val reason: String? = null)

    /** 결제창에서 실패·취소하고 돌아온 화면의 실패 보고. 결제 대기인 자기 후원을 취소 또는 실패로 바꾼다. */
    @Transactional
    fun reportFailure(orderId: String, code: String, message: String, member: LoginMember): Donation {
        val donation = donationRepository.findByOrderId(orderId) ?: throw NotFoundException("후원을 찾을 수 없습니다.")
        Ownership.requireOwner(donation.donor.id, member, "자기 후원만 바꿀 수 있습니다.")
        when (donation.status) {
            DonationStatus.CONFIRMED -> throw ConflictException("이미 확정된 후원입니다.")
            DonationStatus.AWAITING_PAYMENT ->
                if (code == USER_CANCELED_CODE) donation.cancel(code, message) else donation.fail(code, message)
            // 이미 끝난 후원은 그대로 둔다. 실패 화면을 새로고침해도 같은 결과를 보여 준다.
            else -> Unit
        }
        return donation
    }

    @Transactional(readOnly = true)
    fun confirmedOf(type: BroadcastType, broadcastId: Long): List<Donation> =
        donationRepository.findAllByBroadcastTypeAndBroadcastIdAndStatusOrderByConfirmedAtDescIdDesc(
            type, broadcastId, DonationStatus.CONFIRMED,
        )

    private fun record(paymentKey: String?, orderId: String?, source: NotificationSource, result: NotificationResult, detail: String?) {
        notificationRepository.save(PaymentNotification(paymentKey?.take(200), orderId?.take(64), source, result, detail?.take(500), clock.instant()))
    }

    /** 규칙에 따라 요청을 거절한 예외인지. 그 밖의 예외(서버 오류)는 기록하지 않고 되돌린다. */
    private fun RuntimeException.isRejection() =
        this is NotFoundException || this is ForbiddenException || this is ConflictException ||
            this is FieldValidationException || this is BadGatewayException
}

/** 결제창에서 사용자가 결제를 그만두었을 때 토스페이먼츠가 실패 주소로 넘기는 오류 코드. */
private const val USER_CANCELED_CODE = "PAY_PROCESS_CANCELED"

/** 확정할 수 없게 끝난 후원 상태. 이 상태의 주문은 다시 확인해도 거절한다. */
private val CLOSED = setOf(DonationStatus.FAILED, DonationStatus.CANCELED, DonationStatus.NEEDS_REVIEW)

class StartedDonation(val donation: Donation, val orderName: String)
