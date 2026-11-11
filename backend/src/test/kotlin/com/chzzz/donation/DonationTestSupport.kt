package com.chzzz.donation

import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post

/** [session] 회원이 외부 URL 녹화방송을 등록하고 식별자를 돌려준다. */
fun MockMvc.registerRecordingAs(session: MockHttpSession, title: String = "좋은 방송"): Long =
    multipart("/api/recordings") {
        this.session = session
        with(csrf())
        param("title", title)
        param("url", "https://cdn.example.com/video.mp4")
    }.andExpect { status { isCreated() } }
        .andReturn().response.getHeader("Location")!!.substringAfterLast('/').toLong()

/** [session] 회원이 [path] 방송에 후원을 시작하고 주문번호를 돌려준다. */
fun MockMvc.startDonationAs(session: MockHttpSession, path: String, amount: Long): String {
    val body = post(path) {
        this.session = session
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"amount":$amount}"""
    }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
    return Regex("\"orderId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
}

/** 결제 후 돌아온 화면의 확인 요청. [session]이 null이면 비로그인 요청이다. */
fun MockMvc.confirmDonation(session: MockHttpSession?, paymentKey: String, orderId: String, amount: Long): ResultActionsDsl =
    post("/api/donations/confirm") {
        session?.let { this.session = it }
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"paymentKey":"$paymentKey","orderId":"$orderId","amount":$amount}"""
    }

/** 결제창에서 돌아온 실패 화면의 실패 보고. [session]이 null이면 비로그인 요청이다. */
fun MockMvc.reportDonationFailure(session: MockHttpSession?, orderId: String, code: String, message: String): ResultActionsDsl =
    post("/api/donations/fail") {
        session?.let { this.session = it }
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"orderId":"$orderId","code":"$code","message":"$message"}"""
    }

/** 토스가 보내는 결제 상태 변경 웹훅. 로그인 세션·CSRF 토큰 없이 보낸다. */
fun MockMvc.sendTossWebhook(
    paymentKey: String,
    orderId: String,
    status: String = "DONE",
    eventType: String = "PAYMENT_STATUS_CHANGED",
): ResultActionsDsl =
    post("/api/payments/toss/webhook") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"eventType":"$eventType","createdAt":"2026-11-11T10:00:00.000000",""" +
            """"data":{"paymentKey":"$paymentKey","orderId":"$orderId","status":"$status","totalAmount":5000}}"""
    }
