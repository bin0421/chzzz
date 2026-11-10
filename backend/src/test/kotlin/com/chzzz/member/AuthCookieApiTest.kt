package com.chzzz.member

import com.chzzz.BrowserLikeClient
import com.chzzz.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestClient

/** 쿠키 속성과 CSRF 흐름은 실제 서버에 브라우저처럼 요청해서 확인한다. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthCookieApiTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
    }

    @Test
    fun `처음 접속하면 자바스크립트가 읽을 수 있는 CSRF 토큰 쿠키를 받는다`() {
        val browser = BrowserLikeClient(port)

        val status = browser.get("/api/auth/me")

        assertThat(status).isEqualTo(401)
        val xsrf = browser.setCookieHeaders.single { it.startsWith("XSRF-TOKEN=") }
        assertThat(xsrf).doesNotContainIgnoringCase("HttpOnly")
    }

    @Test
    fun `로그인 세션 쿠키는 자바스크립트가 읽을 수 없다`() {
        val browser = BrowserLikeClient(port)

        val status = browser.post(
            "/api/auth/signup",
            {
                contentType(MediaType.APPLICATION_JSON)
                    .body("""{"loginId":"streamer","password":"$TEST_PASSWORD","nickname":"스트리머"}""")
            },
        ) { status, _ -> status }

        assertThat(status).isEqualTo(201)
        val session = browser.setCookieHeaders.last { it.startsWith("JSESSIONID=") }
        assertThat(session).containsIgnoringCase("HttpOnly")
        assertThat(browser.get("/api/auth/me")).isEqualTo(200)
    }

    @Test
    fun `CSRF 토큰 헤더 없이 보낸 상태 변경 요청은 403이고 처리되지 않는다`() {
        val browser = BrowserLikeClient(port)
        browser.get("/api/auth/me")

        val status = RestClient.create("http://localhost:$port").post().uri("/api/auth/signup")
            .header(HttpHeaders.COOKIE, "XSRF-TOKEN=${browser.cookies.getValue("XSRF-TOKEN")}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"loginId":"forged","password":"$TEST_PASSWORD","nickname":"위조"}""")
            .exchange { _, response -> response.statusCode.value() }

        assertThat(status).isEqualTo(403)
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member", Int::class.java)).isZero()
    }
}
