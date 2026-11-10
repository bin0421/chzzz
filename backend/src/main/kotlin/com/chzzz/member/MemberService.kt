package com.chzzz.member

import com.chzzz.common.FieldConflictException
import com.chzzz.common.FieldErrorDetail
import com.chzzz.common.FieldValidationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
) {
    /** 입력 규칙 → 중복 확인 → 저장. 동시에 같은 값으로 가입하면 DB 유니크 제약이 막고, 다시 확인해 같은 409로 응답한다. */
    fun join(loginId: String?, password: String?, nickname: String?): Member {
        val normalizedLoginId = MemberRules.normalizeLoginId(loginId)
        val normalizedNickname = Nickname.normalize(nickname)
        val errors = MemberRules.validate(normalizedLoginId, password, normalizedNickname)
        if (errors.isNotEmpty()) throw FieldValidationException(errors)

        requireNotTaken(normalizedLoginId, normalizedNickname)
        val passwordHash = passwordEncoder.encode(password!!)!!
        try {
            return memberRepository.saveAndFlush(
                Member.join(normalizedLoginId, passwordHash, normalizedNickname, clock.instant()),
            )
        } catch (e: DataIntegrityViolationException) {
            requireNotTaken(normalizedLoginId, normalizedNickname)
            throw e
        }
    }

    /** 아이디·비밀번호가 맞으면 회원, 아니면 null. 어느 쪽이 틀렸는지는 구분하지 않는다. */
    @Transactional(readOnly = true)
    fun authenticate(loginId: String?, password: String?): Member? {
        val member = memberRepository.findByLoginId(MemberRules.normalizeLoginId(loginId)) ?: return null
        if (password.isNullOrEmpty()) return null
        return member.takeIf { passwordEncoder.matches(password, it.passwordHash) }
    }

    private fun requireNotTaken(loginId: String, nickname: String) {
        val conflicts = buildList {
            if (memberRepository.existsByLoginId(loginId)) {
                add(FieldErrorDetail("loginId", "이미 사용 중인 아이디입니다."))
            }
            if (memberRepository.existsByNicknameKey(Nickname.keyOf(nickname))) {
                add(FieldErrorDetail("nickname", "이미 사용 중인 닉네임입니다."))
            }
        }
        if (conflicts.isNotEmpty()) throw FieldConflictException(conflicts)
    }
}
