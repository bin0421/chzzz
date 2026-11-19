package dev.chzzz.app

import java.security.MessageDigest

// 신뢰한 인증서 판단(ADR-0013). 데스크톱 apps/desktop/src/trust.ts와 같은 규칙이다.
// 저장한 서버 주소(출처)에서 난 인증서 오류만 다룬다. 사용자가 받아들인 (출처, SHA-256 지문)과 같으면 받아들이고,
// 기록이 없거나 지문이 바뀌었으면 사용자에게 묻고, 다른 출처는 묻지 않고 거절한다.
sealed interface TrustDecision {
    data object Accept : TrustDecision
    data object Reject : TrustDecision
    data class Ask(val origin: String, val fingerprint: String, val previousFingerprint: String?) : TrustDecision
}

fun decideCertificateTrust(
    url: String,
    fingerprint: String,
    serverOrigin: String?,
    trusted: Map<String, String>,
): TrustDecision {
    if (serverOrigin == null || originOf(url) != serverOrigin) return TrustDecision.Reject

    val previous = trusted[serverOrigin]
    if (previous != null && previous.equals(fingerprint, ignoreCase = true)) return TrustDecision.Accept
    return TrustDecision.Ask(serverOrigin, fingerprint, previous)
}

// 사람이 비교하기 쉬운 형식(AB:CD:...)의 SHA-256 지문. 브라우저 인증서 보기와 같은 형식이다.
fun fingerprintOfDer(der: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(der).joinToString(":") { "%02X".format(it) }
