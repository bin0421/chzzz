package com.chzzz.common

/** 요청한 자원이 없을 때. 404 Problem Details로 응답된다. */
class NotFoundException(message: String) : RuntimeException(message)
