package com.chzzz.live

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

/**
 * MediaMTX 제어 API 어댑터가 MediaMTX 1.21의 모양대로 묻고 읽는지, 이 PC에 띄운 가짜 HTTP 서버로 확인한다.
 * (송출 중 경로: GET /v3/paths/list → items[].name 중 available(옛 이름 ready)이 true인 것, page·pageCount로 페이지)
 */
class MediaMtxApiTest {

    private lateinit var server: HttpServer
    private val requests = mutableListOf<String>()
    private var respond: (query: String?) -> Pair<Int, String> = { 200 to """{"itemCount":0,"pageCount":0,"items":[]}""" }

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += "${exchange.requestMethod} ${exchange.requestURI.path}"
            val (status, body) = respond(exchange.requestURI.query)
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun api() = MediaMtxApi(MediaMtxProperties(apiUrl = "http://127.0.0.1:${server.address.port}"))

    private fun path(name: String, available: Boolean) =
        """{"name":"$name","confName":"~^live/\\d+${'$'}","ready":$available,"available":$available,"online":$available,"tracks":[]}"""

    @Test
    fun `paths list에서 송출 중인 경로 이름만 돌려준다`() {
        respond = {
            200 to """{"itemCount":2,"pageCount":1,"items":[${path("live/1", true)},${path("live/2", false)}]}"""
        }

        val result = api().publishingPaths()

        assertThat(result).isEqualTo(PublishingPaths.Known(setOf("live/1")))
        assertThat(requests).allMatch { it == "GET /v3/paths/list" }
    }

    @Test
    fun `경로가 여러 페이지면 모든 페이지를 읽는다`() {
        respond = { query ->
            when (Regex("page=(\\d+)").find(query.orEmpty())?.groupValues?.get(1) ?: "0") {
                "0" -> 200 to """{"itemCount":2,"pageCount":2,"items":[${path("live/1", true)}]}"""
                "1" -> 200 to """{"itemCount":2,"pageCount":2,"items":[${path("live/2", true)}]}"""
                else -> 200 to """{"itemCount":2,"pageCount":2,"items":[]}"""
            }
        }

        val result = api().publishingPaths()

        assertThat(result).isEqualTo(PublishingPaths.Known(setOf("live/1", "live/2")))
    }

    @Test
    fun `MediaMTX가 오류로 응답하면 송출 여부를 모른다고 돌려준다`() {
        respond = { 401 to """{"status":"error","error":"authentication error"}""" }

        val result = api().publishingPaths()

        assertThat(result).isInstanceOf(PublishingPaths.Unknown::class.java)
    }

    @Test
    fun `MediaMTX에 닿지 않으면 송출 여부를 모른다고 돌려준다`() {
        server.stop(0)

        val result = api().publishingPaths()

        assertThat(result).isInstanceOf(PublishingPaths.Unknown::class.java)
    }
}
