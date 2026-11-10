package com.chzzz.common

import com.chzzz.member.LoginMember

/**
 * 소유자·작성자 검사 규칙. 녹화방송·라이브 방송·댓글이 같은 규칙을 쓴다.
 * 로그인 여부(401)와 대상 존재(404)는 먼저 확인한 뒤 부른다. 소유자가 없는 방송(회원 기능 이전 데이터)은 누구도 통과하지 못한다.
 */
object Ownership {
    fun requireOwner(ownerId: Long?, member: LoginMember, message: String = "이 방송의 소유자만 할 수 있습니다.") {
        if (ownerId == null || ownerId != member.id) throw ForbiddenException(message)
    }
}
