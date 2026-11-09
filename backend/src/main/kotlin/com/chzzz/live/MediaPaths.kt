package com.chzzz.live

/** 라이브 방송과 MediaMTX 경로(`live/{id}`), 브라우저가 부르는 같은 출처 주소(`/media/...`)의 대응. */
object MediaPaths {
    private val pathPattern = Regex("""^live/(\d+)$""")

    fun pathName(id: Long) = "live/$id"

    fun whipUrl(id: Long) = "/media/${pathName(id)}/whip"

    fun whepUrl(id: Long) = "/media/${pathName(id)}/whep"

    /** MediaMTX 경로 이름에서 라이브 방송 식별자를 꺼낸다. 형식이 다르면 null. */
    fun idOf(pathName: String?): Long? = pathName?.let { pathPattern.find(it)?.groupValues?.get(1)?.toLongOrNull() }
}
