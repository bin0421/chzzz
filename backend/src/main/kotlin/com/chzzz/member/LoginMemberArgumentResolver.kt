package com.chzzz.member

import com.chzzz.common.UnauthenticatedException
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * 컨트롤러의 [LoginMember] 파라미터를 세션의 로그인 회원으로 채운다.
 * 로그인하지 않았으면 nullable 파라미터는 null, 아니면 401([UnauthenticatedException])이다.
 * 권한 검사가 URL 규칙이 아니라 각 API 옆에 드러나도록 이 방식을 쓴다.
 */
class LoginMemberArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == LoginMember::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? LoginMember
        if (principal == null && !parameter.isOptional) throw UnauthenticatedException()
        return principal
    }
}
