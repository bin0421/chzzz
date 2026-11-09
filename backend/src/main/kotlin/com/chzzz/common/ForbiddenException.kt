package com.chzzz.common

/** 요청한 일을 할 권한이 없을 때(예: 송출 키가 없거나 틀림). 403 Problem Details로 응답된다. */
class ForbiddenException(message: String) : RuntimeException(message)
