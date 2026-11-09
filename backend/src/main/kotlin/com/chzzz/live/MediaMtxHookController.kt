package com.chzzz.live

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * MediaMTX가 호출하는 내부 엔드포인트. Compose 내부 네트워크에서만 닿아야 하며,
 * 앞단 프록시(Caddy, nginx, Vite)는 `/internal/`을 전달하지 않는다.
 */
@RestController
@RequestMapping("/internal/mediamtx")
class MediaMtxHookController(
    private val service: LiveBroadcastService,
) {
    /** MediaMTX HTTP 인증: 2xx면 허용, 그 밖이면 거부. */
    @PostMapping("/auth")
    fun auth(@RequestBody request: MediaMtxAuthRequest): ResponseEntity<Void> =
        if (service.authorize(request.action, request.path, request.token)) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

    /** MediaMTX `runOnAvailable` 훅: 영상이 들어와 시청할 수 있게 됨. */
    @PostMapping("/available")
    fun available(@RequestParam path: String?): ResponseEntity<Void> {
        service.onStreamAvailable(path)
        return ResponseEntity.ok().build()
    }
}

/** MediaMTX HTTP 인증 요청 본문(필요한 필드만). */
data class MediaMtxAuthRequest(
    val action: String? = null,
    val path: String? = null,
    val token: String? = null,
)
