package com.chzzz.common

/** 로그인이 필요한 요청을 로그인하지 않고 보냈을 때. 401 Problem Details로 응답된다. */
class UnauthenticatedException(message: String = "로그인이 필요합니다.") : RuntimeException(message)
