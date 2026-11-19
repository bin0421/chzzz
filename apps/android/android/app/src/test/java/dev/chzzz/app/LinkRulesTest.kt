package dev.chzzz.app

import org.junit.Assert.assertEquals
import org.junit.Test

// 02 규칙 표(주소 열기 규칙)를 그대로 옮기고, Android 시작 화면 출처(https://localhost) 한 줄을 더한다.
class LinkRulesTest {
    private val server = "https://192.168.0.5:8443"
    private val shell = "https://localhost"

    private fun assertTarget(expected: LinkTarget, urls: List<String>, serverOrigin: String? = server) {
        urls.forEach { url ->
            assertEquals(url, expected, decideLinkTarget(url = url, serverOrigin = serverOrigin, shellOrigin = shell))
        }
    }

    @Test fun `서버 출처의 모든 경로는 앱 안에서 연다`() {
        assertTarget(LinkTarget.APP, listOf("$server/", "$server/recordings/1?x=1#c", server))
        assertTarget(LinkTarget.APP, listOf("http://localhost:3000/live/1"), serverOrigin = "http://localhost:3000")
    }

    @Test fun `https 토스 결제 도메인과 하위 도메인은 앱 안에서 연다`() {
        assertTarget(
            LinkTarget.APP,
            listOf(
                "https://tosspayments.com/",
                "https://payment-gateway-sandbox.tosspayments.com/mobile",
                "https://js.tosspayments.com/v2/standard",
                "https://toss.im/",
                "https://pay.toss.im/payfront",
            ),
        )
    }

    @Test fun `그 밖의 http(s)는 기본 브라우저로 연다`() {
        assertTarget(
            LinkTarget.BROWSER,
            listOf(
                "https://example.com/",
                "http://example.com/",
                "https://192.168.0.5:9443/",
                "http://192.168.0.5:8443/",
                "http://js.tosspayments.com/",
                "https://tosspayments.com.evil.example/",
                "https://eviltosspayments.com/",
                "https://nottoss.im/",
            ),
        )
    }

    @Test fun `서버 주소가 없으면 그 주소도 기본 브라우저로 연다`() {
        assertTarget(LinkTarget.BROWSER, listOf("$server/"), serverOrigin = null)
    }

    @Test fun `다른 앱을 부르는 스킴은 운영체제로 넘긴다`() {
        assertTarget(
            LinkTarget.SYSTEM,
            listOf(
                "mailto:someone@example.com",
                "tel:01000000000",
                "intent://pay#Intent;scheme=supertoss;package=viva.republica.toss;end",
                "supertoss://pay?x=1",
                "ispmobile://TID=1",
            ),
        )
    }

    @Test fun `기기의 파일·스크립트를 여는 스킴과 해석할 수 없는 주소는 막는다`() {
        assertTarget(
            LinkTarget.BLOCK,
            listOf(
                "file:///sdcard/a.txt",
                "javascript:alert(1)",
                "data:text/html,hi",
                "blob:$server/1234-5678",
                "about:blank",
                "content://media/external/images/1",
                "not a url",
                "",
            ),
        )
    }

    @Test fun `서버 화면에서 시작 화면 출처로 가는 이동은 막는다`() {
        assertTarget(LinkTarget.BLOCK, listOf("$shell/", "$shell/index.html#trust"))
    }
}
