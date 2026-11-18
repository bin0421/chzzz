package com.chzzz

import jakarta.servlet.http.Cookie
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcConfigurerAdapter
import org.springframework.web.context.WebApplicationContext

/**
 * MockMvc 테스트에서 `session = ...`으로 넘긴 [MockHttpSession]을 브라우저의 세션 쿠키 저장소처럼 쓰게 한다.
 *
 * 5단계부터 세션은 Spring Session JDBC가 세션 쿠키로 찾는다(ADR-0009). 서블릿 컨테이너의 세션(MockHttpSession)은
 * 보지 않으므로, 응답의 세션 쿠키를 그 MockHttpSession에 기억해 두었다가 다음 요청에 쿠키로 싣는다.
 * 테스트 코드의 `session = viewer` 표현은 "이 브라우저로 요청한다"는 뜻 그대로 남는다.
 *
 * 내장 서버가 없는 MockMvc 환경에서는 Spring Boot가 `server.servlet.session.cookie.*`를 세션 쿠키에 적용하지 않아
 * 쿠키 이름이 Spring Session 기본값(`SESSION`)이 된다. 그래서 이름을 고정하지 않고 CSRF 쿠키를 뺀 응답 쿠키를 기억한다.
 * 실제 서버의 쿠키 이름·속성은 [com.chzzz.member.AuthCookieApiTest]가 확인한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class SessionCookieMockMvcConfiguration {

    @Bean
    fun sessionCookieCarrier(): MockMvcBuilderCustomizer = MockMvcBuilderCustomizer { builder ->
        builder.apply(SessionCookieCarrier)
        builder.alwaysDo { result ->
            val session = result.request.getSession(false) as? MockHttpSession ?: return@alwaysDo
            result.response.cookies.filter { it.name != CSRF_COOKIE }.forEach { cookie ->
                val remembered = rememberedCookies(session)
                if (cookie.maxAge == 0 || cookie.value.isNullOrEmpty()) remembered.remove(cookie.name)
                else remembered[cookie.name] = cookie.value
                session.setAttribute(REMEMBERED_COOKIES, remembered)
            }
        }
    }

    private object SessionCookieCarrier : MockMvcConfigurerAdapter() {
        override fun beforeMockMvcCreated(
            builder: ConfigurableMockMvcBuilder<*>,
            context: WebApplicationContext,
        ): RequestPostProcessor = RequestPostProcessor { request ->
            val session = request.getSession(false) as? MockHttpSession
            if (session != null) {
                val sent = request.cookies.orEmpty().map { it.name }.toSet()
                val extra = rememberedCookies(session).filterKeys { it !in sent }.map { Cookie(it.key, it.value) }
                if (extra.isNotEmpty()) request.setCookies(*request.cookies.orEmpty(), *extra.toTypedArray())
            }
            request
        }
    }

    companion object {
        private const val CSRF_COOKIE = "XSRF-TOKEN"
        private const val REMEMBERED_COOKIES = "test.remembered-cookies"

        @Suppress("UNCHECKED_CAST")
        private fun rememberedCookies(session: MockHttpSession): MutableMap<String, String> =
            (session.getAttribute(REMEMBERED_COOKIES) as? MutableMap<String, String>) ?: linkedMapOf()

        /** 테스트 브라우저([MockHttpSession])가 지금 가진 세션 쿠키 값. 없으면 null. */
        fun sessionCookieOf(session: MockHttpSession): String? = rememberedCookies(session).values.singleOrNull()
    }
}
