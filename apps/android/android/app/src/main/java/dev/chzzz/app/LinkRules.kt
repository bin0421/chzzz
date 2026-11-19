package dev.chzzz.app

import java.net.URI

// 주소 열기 규칙: 앱 창이 이동하거나 새 창을 열려는 주소를 어디서 열지 정한다. 데스크톱 apps/desktop/src/links.ts와 같은 규칙이다.
// - APP: 저장한 서버 출처, 토스 결제위젯·결제창(https) → 앱 안에서 연다
// - BROWSER: 그 밖의 http(s) → 기본 브라우저
// - SYSTEM: 다른 앱을 부르는 스킴(mailto:, tel:, intent://, 카드사·토스 앱 스킴) → 운영체제에 넘긴다
// - BLOCK: 기기의 파일·스크립트를 여는 스킴, 해석할 수 없는 주소, 시작 화면 출처 → 열지 않는다
enum class LinkTarget { APP, BROWSER, SYSTEM, BLOCK }

// 2026-11-19 결제위젯 테스트 결제에서 실제로 본 도메인(하위 도메인 포함). 목록은 02 티켓 기록을 따른다.
val TOSS_PAYMENT_DOMAINS = listOf("tosspayments.com", "toss.im")

// content:은 Android 앱 내부 파일을 여는 스킴이라 데스크톱 목록에 더해 막는다.
private val BLOCKED_SCHEMES = setOf("file", "javascript", "data", "blob", "about", "vbscript", "content")
private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")

fun decideLinkTarget(url: String, serverOrigin: String?, shellOrigin: String? = null): LinkTarget {
    val scheme = SCHEME.find(url.trim())?.groupValues?.get(1)?.lowercase() ?: return LinkTarget.BLOCK
    // blob:은 만든 쪽 출처를 품고 있어 서버 주소처럼 보이므로 먼저 막는다.
    if (scheme in BLOCKED_SCHEMES) return LinkTarget.BLOCK
    if (scheme != "http" && scheme != "https") return LinkTarget.SYSTEM

    val origin = originOf(url) ?: return LinkTarget.BLOCK
    // 시작 화면은 앱 다리를 쓰므로, 서버 화면이 그쪽으로 이동하지 못하게 한다. 시작 화면은 앱이 직접 연다.
    if (shellOrigin != null && origin == shellOrigin) return LinkTarget.BLOCK
    if (serverOrigin != null && origin == serverOrigin) return LinkTarget.APP
    if (scheme == "https" && isTossPaymentHost(URI(url.trim()).host.lowercase())) return LinkTarget.APP
    return LinkTarget.BROWSER
}

private fun isTossPaymentHost(host: String): Boolean =
    TOSS_PAYMENT_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }
