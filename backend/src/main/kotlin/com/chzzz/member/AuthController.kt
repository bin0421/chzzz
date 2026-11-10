package com.chzzz.member

import com.chzzz.common.UnauthenticatedException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val memberService: MemberService,
    private val securityContextRepository: SecurityContextRepository,
) {
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(
        @RequestBody body: SignUpRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): MemberResponse {
        val member = memberService.join(body.loginId, body.password, body.nickname)
        val loginMember = LoginMember(member.id!!, member.nickname)
        startSession(loginMember, request, response)
        return MemberResponse.from(loginMember)
    }

    @PostMapping("/login")
    fun login(
        @RequestBody body: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): MemberResponse {
        val member = memberService.authenticate(body.loginId, body.password)
            ?: throw UnauthenticatedException("아이디 또는 비밀번호가 올바르지 않습니다.")
        val loginMember = LoginMember(member.id!!, member.nickname)
        startSession(loginMember, request, response)
        return MemberResponse.from(loginMember)
    }

    @GetMapping("/me")
    fun me(member: LoginMember): MemberResponse = MemberResponse.from(member)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Suppress("UNUSED_PARAMETER") member: LoginMember, request: HttpServletRequest) {
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
    }

    /** 로그인 세션을 만든다. 이미 세션이 있으면 세션 ID를 바꿔 세션 고정 공격을 막는다. */
    private fun startSession(member: LoginMember, request: HttpServletRequest, response: HttpServletResponse) {
        if (request.getSession(false) != null) request.changeSessionId()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken.authenticated(member, null, emptyList())
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)
    }
}

data class LoginRequest(
    val loginId: String?,
    val password: String?,
)

data class SignUpRequest(
    val loginId: String?,
    val password: String?,
    val nickname: String?,
)

/** 응답에는 회원 식별자와 닉네임만 나간다(아이디·비밀번호 해시 제외). */
data class MemberResponse(
    val id: Long,
    val nickname: String,
) {
    companion object {
        fun from(member: LoginMember) = MemberResponse(member.id, member.nickname)

        fun from(member: Member) = MemberResponse(member.id!!, member.nickname)
    }
}
