package com.chzzz.common

/** 현재 상태에서는 할 수 없는 요청일 때(예: 이미 종료된 라이브 방송을 종료). 409 Problem Details로 응답된다. */
class ConflictException(message: String) : RuntimeException(message)
