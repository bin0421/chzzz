package com.chzzz.live

/** 라이브 방송과 MediaMTX 경로(`live/{id}`), 브라우저가 부르는 같은 출처 주소(`/media/...`)의 대응. */
object MediaPaths {
    private val pathPattern = Regex("""live/([1-9]\d*)""")

    fun pathName(id: Long) = "live/$id"

    fun whipUrl(id: Long) = "/media/${pathName(id)}/whip"

    fun whepUrl(id: Long) = "/media/${pathName(id)}/whep"

    /**
     * MediaMTX 경로 이름에서 라이브 방송 식별자를 꺼낸다. [pathName]과 정확히 같은 정규형(`live/{id}`)만
     * 인정하고, 그 밖(`live/01`, 끝 개행 등 같은 방송을 다른 경로로 가리키는 것 포함)은 null.
     */
    fun idOf(pathName: String?): Long? =
        pathName?.let { pathPattern.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }
}
