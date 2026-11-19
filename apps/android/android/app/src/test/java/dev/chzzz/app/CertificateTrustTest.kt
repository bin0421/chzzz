package dev.chzzz.app

import org.junit.Assert.assertEquals
import org.junit.Test

// 02 규칙 표(신뢰한 인증서 판단)를 그대로 옮긴다.
class CertificateTrustTest {
    private val server = "https://192.168.0.5:8443"
    private val fpA = "AA:BB:CC:DD"
    private val fpB = "11:22:33:44"

    private fun decide(url: String, trusted: Map<String, String>, serverOrigin: String? = server, fingerprint: String = fpA) =
        decideCertificateTrust(url = url, fingerprint = fingerprint, serverOrigin = serverOrigin, trusted = trusted)

    @Test fun `저장한 출처에서 같은 지문이면 받아들인다`() {
        assertEquals(TrustDecision.Accept, decide("$server/recordings/1", mapOf(server to fpA)))
    }

    @Test fun `지문 대소문자는 가리지 않는다`() {
        assertEquals(TrustDecision.Accept, decide("$server/recordings/1", mapOf(server to fpA.lowercase())))
    }

    @Test fun `기록이 없으면 이전 지문 없이 묻는다`() {
        assertEquals(TrustDecision.Ask(server, fpA, null), decide("$server/", emptyMap()))
    }

    @Test fun `지문이 바뀌면 이전 지문과 함께 다시 묻는다`() {
        assertEquals(
            TrustDecision.Ask(server, fpB, fpA),
            decide("$server/media/live/x/whep", mapOf(server to fpA), fingerprint = fpB),
        )
    }

    @Test fun `다른 출처의 기록은 쓰지 않는다`() {
        assertEquals(TrustDecision.Ask(server, fpA, null), decide(server, mapOf("https://192.168.0.7:8443" to fpA)))
    }

    @Test fun `저장한 출처가 아니면 묻지 않고 거절한다`() {
        listOf(
            "https://evil.example.com/",
            "https://192.168.0.5:9443/",
            "https://js.tosspayments.com/v2/standard",
            "not a url",
        ).forEach { url -> assertEquals(url, TrustDecision.Reject, decide(url, mapOf(server to fpA))) }
    }

    @Test fun `저장한 서버 주소가 없으면 거절한다`() {
        assertEquals(TrustDecision.Reject, decide(server, mapOf(server to fpA), serverOrigin = null))
    }

    @Test fun `지문은 DER의 SHA-256을 대문자 16진수 콜론 형식으로 쓴다`() {
        assertEquals(
            "AE:4B:32:80:E5:6E:2F:AF:83:F4:14:A6:E3:DA:BE:9D:5F:BE:18:97:65:44:C0:5F:ED:12:1A:CC:B8:5B:53:FC",
            fingerprintOfDer(byteArrayOf(0, 1, 2)),
        )
    }
}
