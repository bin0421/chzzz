package com.chzzz.common

import com.chzzz.member.LoginMemberArgumentResolver
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.filter.OncePerRequestFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 인증: 서버 세션 + CSRF 토큰(ADR-0005).
 * URL별 접근 규칙은 두지 않고 모든 요청을 통과시킨다. 로그인 필요 여부는 각 API의 [com.chzzz.member.LoginMember]
 * 파라미터가, 소유자·작성자 검사는 서비스가 한다. 필터 단계에서는 세션 복원과 CSRF 검사만 한다.
 */
@Configuration
class SecurityConfig : WebMvcConfigurer {

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun securityFilterChain(http: HttpSecurity, securityContextRepository: SecurityContextRepository): SecurityFilterChain {
        http
            .securityContext { it.securityContextRepository(securityContextRepository) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .csrf {
                it.spa()
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers("/internal/**")
            }
            .addFilterAfter(CsrfCookieFilter(), CsrfFilter::class.java)
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    writeProblem(response, HttpStatus.UNAUTHORIZED, "로그인 필요", "로그인이 필요합니다.")
                }
                it.accessDeniedHandler { _, response, _ ->
                    writeProblem(
                        response, HttpStatus.FORBIDDEN, "요청 거절",
                        "요청을 확인할 수 없습니다. 새로고침한 뒤 다시 시도해 주세요.",
                    )
                }
            }
        return http.build()
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(LoginMemberArgumentResolver())
    }

    private fun writeProblem(response: HttpServletResponse, status: HttpStatus, title: String, detail: String) {
        response.status = status.value()
        response.contentType = "application/problem+json"
        response.characterEncoding = "UTF-8"
        response.writer.write(
            """{"type":"about:blank","title":"$title","status":${status.value()},"detail":"$detail"}""",
        )
    }
}

/**
 * CSRF 토큰은 처음 쓰일 때 쿠키로 저장된다. 응답 본문을 쓰기 전에 토큰을 읽어 두어
 * 모든 응답(401 같은 오류 응답 포함)에 XSRF-TOKEN 쿠키가 실리게 한다.
 */
private class CsrfCookieFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        (request.getAttribute(CsrfToken::class.java.name) as? CsrfToken)?.token
        filterChain.doFilter(request, response)
    }
}
