package com.chzzz

import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

/**
 * 실제 서버에 브라우저처럼 요청하는 테스트용 클라이언트. 받은 쿠키(세션, CSRF 토큰)를 기억해 다음 요청에 싣고,
 * 상태를 바꾸는 요청에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보낸다.
 *
 * [onPort]로 같은 쿠키를 가진 채 다른 서버(인스턴스)에 요청할 수 있다. 로드밸런서 뒤의 브라우저 하나를 흉내 낸다.
 *
 * MockMvc의 `csrf()`는 공유 CSRF 필터의 토큰 저장소를 바꿔 두므로, 실제 서버 테스트는 MockMvc와 같은 테스트 클래스에 두지 않는다.
 */
class BrowserLikeClient(
    port: Int,
    val cookies: MutableMap<String, String> = linkedMapOf(),
) {
    private val client = RestClient.create("http://localhost:$port")
    val setCookieHeaders = mutableListOf<String>()

    /** 같은 쿠키 저장소를 함께 쓰는, 다른 포트의 서버로 가는 클라이언트. */
    fun onPort(otherPort: Int): BrowserLikeClient = BrowserLikeClient(otherPort, cookies)

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
