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
        val streamer = broadcast.owner
        if (streamer == null || streamer.id == donor.id) throw ConflictException("후원할 수 없는 방송입니다.")
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
        record(paymentKey, orderId, NotificationSource.CONFIRM_REQUEST, outcome.result, outcome.reason)
        return outcome.donation
    }

    private fun approve(paymentKey: String, orderId: String, amount: Long, member: LoginMember): ConfirmOutcome {
        val donation = donationRepository.findForUpdateByOrderId(orderId) ?: throw NotFoundException("후원을 찾을 수 없습니다.")
        Ownership.requireOwner(donation.donor.id, member, "자기 후원만 확인할 수 있습니다.")
        if (donation.status == DonationStatus.CONFIRMED) {
            // 이미 확정: 결제 서비스를 다시 부르지 않고 기존 결과를 돌려준다(새로고침·재전송).
            if (donation.paymentKey == paymentKey) return ConfirmOutcome(donation, NotificationResult.ALREADY_CONFIRMED)
            throw ConflictException("이미 다른 결제로 확정된 후원입니다.")
        }
        if (donation.status in CLOSED) throw ConflictException("이미 실패했거나 취소된 후원입니다.")
        // 결제 하나는 후원 하나에만 붙는다. 저장 구조의 유니크 제약이 마지막으로 막지만, 그 전에 이유와 함께 거절한다.
        if (donationRepository.existsByPaymentKey(paymentKey)) throw ConflictException("이미 다른 후원에 쓰인 결제입니다.")
        if (amount != donation.amount) {
            donation.fail("AMOUNT_MISMATCH", "결제 금액이 후원 금액과 다릅니다.")
            throw FieldValidationException("amount", "결제 금액이 후원 금액과 다릅니다.")
        }
        if (failIfStreamerChanged(donation)) throw ConflictException(STREAMER_CHANGED_MESSAGE)
        val approved = gateway.confirm(paymentKey, orderId, amount)
        // 승인은 이미 끝났는데 서버가 확정하기 전에 끊긴 경우. 조회로 실제 상태를 확인해 같은 규칙으로 처리한다.
        val payment = if (approved is GatewayResult.Rejected && approved.code == ALREADY_PROCESSED_CODE) gateway.find(paymentKey) else approved
        val reason = when (val result = payment) {
            is GatewayResult.Payment -> when {
                !result.isDone -> "결제가 완료되지 않았습니다(결제 상태 ${result.status})."
                    .also { donation.fail("PAYMENT_NOT_DONE", it) }
                result.orderId != donation.orderId || result.totalAmount != donation.amount ->
                    "결제 서비스의 승인 내용(주문 ${result.orderId}, ${result.totalAmount}원)이 후원과 맞지 않아 확인이 필요합니다."
                        .also { donation.needsReview("PAYMENT_MISMATCH", it) }
                else -> {
                    donation.confirm(paymentKey, clock.instant())
                    return ConfirmOutcome(donation, NotificationResult.NEW_CONFIRMED)
                }
            }
            is GatewayResult.Rejected -> result.message.also { donation.fail(result.code, it) }
            // 결과를 모르므로 결제 대기로 두고, 같은 요청을 다시 보내면 다시 확인한다.
            is GatewayResult.Unavailable -> throw BadGatewayException("결제 서비스와 연결하지 못했습니다. 잠시 후 다시 확인해 주세요.")
        }
        return ConfirmOutcome(donation, NotificationResult.REJECTED, reason)
    }

    private class ConfirmOutcome(val donation: Donation, val result: NotificationResult, val reason: String? = null)

    /** 방송이 사라졌거나 소유자가 저장된 방송자와 다르면 후원을 실패로 바꾸고 true. */
    private fun failIfStreamerChanged(donation: Donation): Boolean {
        val owner = broadcasts.find(donation.broadcastType, donation.broadcastId)?.owner
        if (owner?.id == donation.streamer.id) return false
        donation.fail("STREAMER_CHANGED", STREAMER_CHANGED_MESSAGE)
        return true
    }

    /**
     * 토스 결제 상태 변경 웹훅. 본문은 믿지 않고 결제 키로 조회한 실제 결제로 확인 요청과 같은 규칙을 적용한다(ADR-0008).
     * 위조·관계없는 통지는 무시한다. 결과는 응답이 아니라 통지 기록에 남는다.
     */
    @Transactional
    fun handleWebhook(eventType: String, paymentKey: String, bodyOrderId: String) {
        val (result, detail, orderId) = judgeWebhook(eventType, paymentKey, bodyOrderId)
        record(paymentKey, orderId ?: bodyOrderId, NotificationSource.WEBHOOK, result, detail)
    }

    private fun judgeWebhook(eventType: String, paymentKey: String, bodyOrderId: String): Triple<NotificationResult, String?, String?> {
        fun ignored(detail: String, orderId: String? = null) = Triple(NotificationResult.IGNORED, detail, orderId)
        if (eventType != PAYMENT_STATUS_CHANGED || paymentKey.isBlank()) return ignored("결제 상태 변경 통지가 아닙니다(이벤트 $eventType).")
        val payment = when (val found = gateway.find(paymentKey)) {
            is GatewayResult.Payment -> found
            is GatewayResult.Rejected -> return ignored("결제 서비스에 없는 결제입니다(${found.code}).")
            // 확인하지 못했으므로 적립하지 않는다. 결제 대기로 남아 다음 통지(확인 요청·웹훅 재전송)에서 다시 확인된다.
            is GatewayResult.Unavailable -> return Triple(NotificationResult.REJECTED, "결제 서비스에서 결제를 조회하지 못했습니다.", null)
        }
        if (payment.orderId != bodyOrderId) return ignored("통지의 주문번호가 결제 서비스의 주문번호(${payment.orderId})와 다릅니다.", payment.orderId)
        val donation = donationRepository.findForUpdateByOrderId(payment.orderId)
            ?: return ignored("결제의 주문번호에 해당하는 후원이 없습니다.", payment.orderId)
        fun outcome(result: NotificationResult, detail: String? = null) = Triple(result, detail, donation.orderId)
        if (!payment.isDone) return outcome(NotificationResult.IGNORED, "결제가 완료되지 않았습니다(결제 상태 ${payment.status}).")
        if (payment.totalAmount != donation.amount) return outcome(NotificationResult.IGNORED, "결제 금액(${payment.totalAmount}원)이 후원 금액과 다릅니다.")
        return when {
            donation.status == DonationStatus.CONFIRMED && donation.paymentKey == paymentKey -> outcome(NotificationResult.ALREADY_CONFIRMED)
            donation.status == DonationStatus.CONFIRMED -> outcome(NotificationResult.IGNORED, "이미 다른 결제로 확정된 후원입니다.")
            donation.status in CLOSED -> outcome(NotificationResult.REJECTED, "이미 실패했거나 취소된 후원입니다.")
            donationRepository.existsByPaymentKey(paymentKey) -> outcome(NotificationResult.IGNORED, "이미 다른 후원에 쓰인 결제입니다.")
            failIfStreamerChanged(donation) -> outcome(NotificationResult.REJECTED, STREAMER_CHANGED_MESSAGE)
            else -> {
                donation.confirm(paymentKey, clock.instant())
                outcome(NotificationResult.NEW_CONFIRMED)
            }
        }
    }

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
    fun confirmedOf(type: BroadcastType, broadcastId: Long): List<Donation> {
        broadcasts.find(type, broadcastId) ?: throw NotFoundException("방송을 찾을 수 없습니다.")
        return donationRepository.findAllByBroadcastTypeAndBroadcastIdAndStatusOrderByConfirmedAtDescIdDesc(
            type, broadcastId, DonationStatus.CONFIRMED,
        )
    }

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

/** 이미 승인된 결제에 다시 승인을 요청했을 때 토스페이먼츠가 주는 오류 코드. */
private const val ALREADY_PROCESSED_CODE = "ALREADY_PROCESSED_PAYMENT"

/** 토스페이먼츠 웹훅 중 결제 상태가 바뀌었다는 이벤트 이름. */
private const val PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED"

private const val STREAMER_CHANGED_MESSAGE = "방송이 사라졌거나 방송자가 바뀌어 후원할 수 없습니다."

/** 확정할 수 없게 끝난 후원 상태. 이 상태의 주문은 다시 확인해도 거절한다. */
private val CLOSED = setOf(DonationStatus.FAILED, DonationStatus.CANCELED, DonationStatus.NEEDS_REVIEW)

class StartedDonation(val donation: Donation, val orderName: String)
