package dev.chzzz.app

import org.junit.Assert.assertEquals
import org.junit.Test

// 시작 화면이 보낸 서버 주소를 앱이 다시 확인한다(시작 화면 parseServerAddress와 같은 허용 범위, 출처만).
class ServerOriginTest {
    @Test fun `https 출처와 같은 기기의 http localhost·127_0_0_1 출처는 받는다`() {
        listOf("https://192.168.0.5:8443", "https://chzzz.example.com", "http://localhost:3000", "http://127.0.0.1:3000")
            .forEach { assertEquals(it, it, validServerOrigin(it)) }
    }

    @Test fun `출처가 아니거나 허용하지 않는 주소는 거절한다`() {
        listOf(
            "https://192.168.0.5:8443/recordings",
            "https://192.168.0.5:443",
            "HTTPS://192.168.0.5:8443",
            "http://192.168.0.5:8443",
            "https://user:pw@192.168.0.5:8443",
            "ftp://192.168.0.5",
            "file:///sdcard",
            "192.168.0.5:8443",
            "",
            null,
        ).forEach { assertEquals(it, null, validServerOrigin(it)) }
    }
}
