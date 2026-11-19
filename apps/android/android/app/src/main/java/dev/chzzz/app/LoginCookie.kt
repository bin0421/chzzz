package dev.chzzz.app

// 서버의 로그인 쿠키(JSESSIONID)는 만료 시각이 없는 세션 쿠키라 앱 프로세스가 끝나면 WebView가 버린다.
// 서버 출처의 쿠키에 JSESSIONID가 있으면 같은 값에 30일 만료를 붙인 Set-Cookie 문자열을 만들어 다시 저장한다.
// 로그인이 실제로 유효한지는 여전히 서버 세션(만료·로그아웃)이 정한다. 데스크톱 apps/desktop/src/cookies.ts와 같은 목적이다.
const val LOGIN_COOKIE_NAME = "JSESSIONID"
private const val KEEP_SECONDS = 30 * 24 * 60 * 60

fun persistentLoginCookie(cookieHeader: String?, serverOrigin: String?): String? {
    if (cookieHeader == null || serverOrigin == null) return null
    val value = cookieHeader.split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("$LOGIN_COOKIE_NAME=") }
        ?.substringAfter("=")
    if (value.isNullOrEmpty()) return null

    val secure = if (serverOrigin.startsWith("https://")) "; Secure" else ""
    return "$LOGIN_COOKIE_NAME=$value; Max-Age=$KEEP_SECONDS; Path=/$secure; HttpOnly; SameSite=Lax"
}
