package dev.chzzz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 데스크톱 로그인 유지(01)와 같은 목적: 만료 없는 로그인 쿠키(JSESSIONID)를 30일 만료 쿠키로 다시 저장한다.
// Android CookieManager는 쿠키의 이름=값만 돌려주므로 서버 출처의 쿠키 문자열에서 JSESSIONID를 찾는다.
class LoginCookieTest {
    @Test fun `서버의 JSESSIONID를 같은 값으로 30일 만료 쿠키로 바꾼다`() {
        assertEquals(
            "JSESSIONID=abc123; Max-Age=2592000; Path=/; Secure; HttpOnly; SameSite=Lax",
            persistentLoginCookie("XSRF-TOKEN=t1; JSESSIONID=abc123", "https://192.168.0.5:8443"),
        )
    }

    @Test fun `http localhost 서버면 Secure를 붙이지 않는다`() {
        assertEquals(
            "JSESSIONID=abc; Max-Age=2592000; Path=/; HttpOnly; SameSite=Lax",
            persistentLoginCookie("JSESSIONID=abc", "http://localhost:3000"),
        )
    }

    @Test fun `로그인 쿠키가 없거나 서버 주소가 없으면 아무것도 하지 않는다`() {
        assertNull(persistentLoginCookie("XSRF-TOKEN=t1", "https://192.168.0.5:8443"))
        assertNull(persistentLoginCookie("MYJSESSIONID=x", "https://192.168.0.5:8443"))
        assertNull(persistentLoginCookie(null, "https://192.168.0.5:8443"))
        assertNull(persistentLoginCookie("JSESSIONID=abc", null))
        assertNull(persistentLoginCookie("JSESSIONID=", "https://192.168.0.5:8443"))
    }
}
