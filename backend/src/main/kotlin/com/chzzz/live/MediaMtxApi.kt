package com.chzzz.live

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration

/** MediaMTX 제어 API 주소. 비우면 송출 대조를 하지 않는다(ADR-0010). */
@ConfigurationProperties("mediamtx")
data class MediaMtxProperties(
    val apiUrl: String = "",
)

/** MediaMTX 제어 API(`/v3/paths/list`)로 송출 중인 경로를 읽는다. */
@Component
class MediaMtxApi(properties: MediaMtxProperties) : MediaServer {
    private val client = RestClient.builder()
        .baseUrl(properties.apiUrl)
        // MediaMTX가 멈춰 있어도 주기 작업이 오래 묶이지 않게 짧게 기다린다.
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(TIMEOUT)
            setReadTimeout(TIMEOUT)
        })
        .build()

    /** 송출 중인 경로를 모든 페이지에서 모은다. 연결 실패·오류 응답이면 [PublishingPaths.Unknown]. */
    override fun publishingPaths(): PublishingPaths = try {
        readAllPages()
    } catch (e: RestClientException) {
        PublishingPaths.Unknown(e.message ?: "MediaMTX 제어 API와 통신하지 못했습니다.")
    }

    private fun readAllPages(): PublishingPaths {
        val names = mutableSetOf<String>()
        var page = 0
        do {
            val body = client.get().uri("/v3/paths/list?page={page}", page).retrieve().body(Map::class.java)!!
            (body["items"] as List<*>).map { it as Map<*, *> }
                .filter { it["available"] == true }
                .mapTo(names) { it["name"].toString() }
            val pageCount = (body["pageCount"] as Number).toInt()
        } while (++page < pageCount)
        return PublishingPaths.Known(names)
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(3)
    }
}

@Configuration
@EnableConfigurationProperties(MediaMtxProperties::class)
class MediaMtxConfiguration
