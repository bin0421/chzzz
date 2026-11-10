package com.chzzz

import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

/**
 * 실제 서버에 브라우저처럼 요청하는 테스트용 클라이언트. 받은 쿠키(세션, CSRF 토큰)를 기억해 다음 요청에 싣고,
 * 상태를 바꾸는 요청에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보낸다.
 *
 * MockMvc의 `csrf()`는 공유 CSRF 필터의 토큰 저장소를 바꿔 두므로, 실제 서버 테스트는 MockMvc와 같은 테스트 클래스에 두지 않는다.
 */
class BrowserLikeClient(port: Int) {
    private val client = RestClient.create("http://localhost:$port")
    val cookies = linkedMapOf<String, String>()
    val setCookieHeaders = mutableListOf<String>()

    fun get(path: String): Int = client.get().uri(path)
        .header(HttpHeaders.COOKIE, cookieHeader())
        .exchange { _, response -> remember(response.headers); response.statusCode.value() }

    fun <T> post(path: String, customize: RestClient.RequestBodySpec.() -> Unit, read: (Int, HttpHeaders) -> T): T {
        if ("XSRF-TOKEN" !in cookies) get("/api/auth/me")
        val spec = client.post().uri(path)
            .header(HttpHeaders.COOKIE, cookieHeader())
            .header("X-XSRF-TOKEN", cookies.getValue("XSRF-TOKEN"))
        spec.customize()
        return spec.exchange { _, response ->
            remember(response.headers)
            read(response.statusCode.value(), response.headers)
        }
    }

    private fun cookieHeader(): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun remember(headers: HttpHeaders) {
        headers[HttpHeaders.SET_COOKIE].orEmpty().forEach { setCookie ->
            setCookieHeaders += setCookie
            val (name, value) = setCookie.substringBefore(";").split("=", limit = 2)
            if (value.isEmpty()) cookies.remove(name) else cookies[name] = value
        }
    }
}
