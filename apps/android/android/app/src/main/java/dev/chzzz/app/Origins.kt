package dev.chzzz.app

import java.net.URI

// 주소의 출처(스킴://호스트[:포트])를 브라우저 URL.origin과 같은 모양으로 돌려준다.
// http(s)만 다루고, 기본 포트(80·443)는 뺀다. 해석할 수 없거나 http(s)가 아니면 null.
fun originOf(url: String): String? {
    val uri = try {
        URI(url.trim())
    } catch (_: Exception) {
        return null
    }
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    if (uri.rawAuthority == null || uri.rawUserInfo != null) return null
    val host = uri.host?.lowercase() ?: return null
    val port = uri.port
    val defaultPort = if (scheme == "https") 443 else 80
    return if (port == -1 || port == defaultPort) "$scheme://$host" else "$scheme://$host:$port"
}
