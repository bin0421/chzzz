package com.chzzz.common

/** 바깥 서비스(결제 서비스 등)와 통신하지 못해 결과를 모를 때. 502 Problem Details로 응답되며 같은 요청을 다시 보낼 수 있다. */
class BadGatewayException(message: String) : RuntimeException(message)
