package com.chzzz.live

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** 송출 키: 라이브 방송을 시작할 때 송출자에게만 주는 비밀 값. DB에는 해시만 남긴다. */
object StreamKey {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(streamKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(streamKey.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** 상수 시간 비교. */
    fun matches(streamKey: String?, hash: String): Boolean {
        if (streamKey.isNullOrEmpty()) return false
        return MessageDigest.isEqual(hash(streamKey).toByteArray(), hash.toByteArray())
    }
}
