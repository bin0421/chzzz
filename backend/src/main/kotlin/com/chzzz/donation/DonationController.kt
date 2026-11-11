package com.chzzz.donation

import com.chzzz.common.BroadcastType
import com.chzzz.member.LoginMember
import com.chzzz.member.MemberResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class DonationController(
    private val donationService: DonationService,
    private val paymentProperties: PaymentProperties,
) {
    @GetMapping("/api/payments/config")
    fun config() = PaymentConfigResponse(paymentProperties.clientKey)

    @PostMapping("/api/recordings/{id}/donations")
    @ResponseStatus(HttpStatus.CREATED)
    fun startForRecording(@PathVariable id: Long, @RequestBody body: StartDonationRequest, member: LoginMember) =
        start(BroadcastType.RECORDING, id, body, member)

    @PostMapping("/api/live-broadcasts/{id}/donations")
    @ResponseStatus(HttpStatus.CREATED)
    fun startForLiveBroadcast(@PathVariable id: Long, @RequestBody body: StartDonationRequest, member: LoginMember) =
        start(BroadcastType.LIVE_BROADCAST, id, body, member)

    @PostMapping("/api/donations/confirm")
    fun confirm(@RequestBody body: ConfirmDonationRequest, member: LoginMember): DonationResultResponse =
        DonationResultResponse.from(donationService.confirm(body.paymentKey.orEmpty(), body.orderId.orEmpty(), body.amount ?: 0, member))

    @PostMapping("/api/donations/fail")
    fun reportFailure(@RequestBody body: ReportDonationFailureRequest, member: LoginMember): DonationFailureResponse {
        val donation = donationService.reportFailure(body.orderId.orEmpty(), body.code.orEmpty(), body.message.orEmpty(), member)
        return DonationFailureResponse(donation.status, BroadcastRef(donation.broadcastType, donation.broadcastId))
    }

    /**
     * 토스 결제 상태 변경 웹훅. 로그인·CSRF 없이 받는다. 처리 결과는 통지 기록에 남고 응답은 200이다(ADR-0008).
     * 결제 서비스 조회가 통신 실패한 경우만 502로 답해 재전송을 받는다.
     */
    @PostMapping("/api/payments/toss/webhook")
    fun tossWebhook(@RequestBody body: TossWebhookRequest) {
        donationService.handleWebhook(body.eventType.orEmpty(), body.data?.paymentKey.orEmpty(), body.data?.orderId.orEmpty())
    }

    @GetMapping("/api/recordings/{id}/donations")
    fun listForRecording(@PathVariable id: Long) = list(BroadcastType.RECORDING, id)

    @GetMapping("/api/live-broadcasts/{id}/donations")
    fun listForLiveBroadcast(@PathVariable id: Long) = list(BroadcastType.LIVE_BROADCAST, id)

    private fun start(type: BroadcastType, id: Long, body: StartDonationRequest, member: LoginMember): StartedDonationResponse {
        val started = donationService.start(type, id, DonationAmount.parse(body.amount), member)
        return StartedDonationResponse(
            orderId = started.donation.orderId,
            amount = started.donation.amount,
            orderName = started.orderName,
            streamer = MemberResponse.from(started.donation.streamer),
            // 결제 서비스가 구매자를 구분하는 값. 회원 식별자를 그대로 노출하지 않게 주문마다 새로 만든다.
            customerKey = "donor-" + started.donation.orderId,
        )
    }

    private fun list(type: BroadcastType, id: Long): DonationListResponse {
        val donations = donationService.confirmedOf(type, id)
        return DonationListResponse(
            totalAmount = donations.sumOf { it.amount },
            count = donations.size,
            items = donations.map { DonationItemResponse(it.id!!, MemberResponse.from(it.donor), it.amount, it.confirmedAt!!) },
        )
    }
}

data class PaymentConfigResponse(val clientKey: String)

data class StartDonationRequest(val amount: Any?)

data class ConfirmDonationRequest(val paymentKey: String?, val orderId: String?, val amount: Long?)

data class ReportDonationFailureRequest(val orderId: String?, val code: String?, val message: String?)

/** 토스 웹훅 본문. 결제 키(와 대조용 주문번호)만 읽고 나머지 값은 믿지 않는다. */
data class TossWebhookRequest(val eventType: String?, val data: TossWebhookData?)

data class TossWebhookData(val paymentKey: String?, val orderId: String?)

data class DonationFailureResponse(val status: DonationStatus, val broadcast: BroadcastRef)

data class StartedDonationResponse(
    val orderId: String,
    val amount: Long,
    val orderName: String,
    val streamer: MemberResponse,
    val customerKey: String,
)

data class BroadcastRef(val type: BroadcastType, val id: Long)

data class DonationResultResponse(
    val id: Long,
    val status: DonationStatus,
    val amount: Long,
    val streamer: MemberResponse,
    val broadcast: BroadcastRef,
    val confirmedAt: Instant?,
    /** 확정되지 않은 이유(실패·확인 필요). 확정이면 없다. */
    val failure: FailureResponse?,
) {
    companion object {
        fun from(donation: Donation) = DonationResultResponse(
            id = donation.id!!,
            status = donation.status,
            amount = donation.amount,
            streamer = MemberResponse.from(donation.streamer),
            broadcast = BroadcastRef(donation.broadcastType, donation.broadcastId),
            confirmedAt = donation.confirmedAt,
            failure = donation.failureCode?.let { FailureResponse(it, donation.failureMessage.orEmpty()) },
        )
    }
}

data class FailureResponse(val code: String, val message: String)

data class DonationItemResponse(val id: Long, val donor: MemberResponse, val amount: Long, val confirmedAt: Instant)

data class DonationListResponse(val totalAmount: Long, val count: Int, val items: List<DonationItemResponse>)
