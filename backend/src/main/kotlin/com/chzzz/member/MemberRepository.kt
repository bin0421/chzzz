package com.chzzz.member

import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByLoginId(loginId: String): Member?

    fun existsByLoginId(loginId: String): Boolean

    fun existsByNicknameKey(nicknameKey: String): Boolean
}
