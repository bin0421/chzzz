package com.chzzz.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 회원: 아이디·비밀번호·닉네임으로 가입한 사람. 아이디와 비밀번호 해시는 화면·응답에 나가지 않는다. */
@Entity
@Table(name = "member")
class Member private constructor(
    @Column(name = "login_id", nullable = false, length = 20)
    val loginId: String,

    @Column(name = "password_hash", nullable = false, length = 100)
    val passwordHash: String,

    @Column(nullable = false, length = 20)
    val nickname: String,

    @Column(name = "nickname_key", nullable = false, length = 20)
    val nicknameKey: String,

    @Column(nullable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    companion object {
        fun join(loginId: String, passwordHash: String, nickname: String, now: Instant): Member =
            Member(loginId, passwordHash, nickname, Nickname.keyOf(nickname), now.truncatedTo(ChronoUnit.MICROS))
    }
}
