package dev.chzzz.app

private val LOCAL_HTTP_ORIGIN = Regex("""^http://(localhost|127\.0\.0\.1)(:\d+)?$""")

// 시작 화면이 보낸 서버 주소가 저장할 수 있는 출처인지 앱 쪽에서 다시 확인한다.
// 시작 화면 parseServerAddress(apps/shell/src/address.ts)가 만든 출처만 그대로 통과한다: https 출처, 또는 http://localhost·127.0.0.1.
fun validServerOrigin(value: String?): String? {
    if (value == null || originOf(value) != value) return null
    return if (value.startsWith("https://") || LOCAL_HTTP_ORIGIN.matches(value)) value else null
}
