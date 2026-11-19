package dev.chzzz.app

import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager

// 연결 확인: 서버 주소의 /api/auth/me 응답 코드를 시작 화면에 돌려준다(시작 화면이 200·401을 chzzz 서버로 본다).
// 공인 인증서가 아니면(Caddy 로컬 CA) 서버 인증서를 직접 받아 신뢰한 인증서 판단을 거친다(ADR-0013).
// 받아들이지 않은 인증서로는 요청을 보내지 않는다.
sealed interface ServerCheckResult {
    data class Status(val code: Int) : ServerCheckResult
    data class Failure(val message: String) : ServerCheckResult
    data class UntrustedCertificate(val ask: TrustDecision.Ask) : ServerCheckResult
}

private const val TIMEOUT_MS = 5000

fun checkServer(origin: String, trusted: Map<String, String>): ServerCheckResult {
    return try {
        ServerCheckResult.Status(request(URL("$origin/api/auth/me").openConnection() as HttpURLConnection))
    } catch (e: SSLException) {
        if (origin.startsWith("https://")) checkWithPinnedCertificate(origin, trusted) else failure(e)
    } catch (e: Exception) {
        failure(e)
    }
}

private fun checkWithPinnedCertificate(origin: String, trusted: Map<String, String>): ServerCheckResult {
    var decision: TrustDecision = TrustDecision.Reject
    val pinning = object : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
            val fingerprint = fingerprintOfDer(chain.first().encoded)
            decision = decideCertificateTrust(url = origin, fingerprint = fingerprint, serverOrigin = origin, trusted = trusted)
            if (decision != TrustDecision.Accept) throw CertificateException("신뢰하지 않은 인증서")
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?) = throw CertificateException()
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(pinning), null) }
    val connection = URL("$origin/api/auth/me").openConnection() as HttpsURLConnection
    connection.sslSocketFactory = context.socketFactory
    // 지문을 고정해 확인했으므로 이름(IP) 검사는 지문을 받아들였을 때만 넘긴다.
    connection.setHostnameVerifier { _, _ -> decision == TrustDecision.Accept }
    return try {
        ServerCheckResult.Status(request(connection))
    } catch (e: Exception) {
        when (val d = decision) {
            is TrustDecision.Ask -> ServerCheckResult.UntrustedCertificate(d)
            TrustDecision.Reject -> ServerCheckResult.Failure("인증서를 거절했습니다.")
            TrustDecision.Accept -> failure(e)
        }
    }
}

private fun request(connection: HttpURLConnection): Int {
    connection.instanceFollowRedirects = false
    connection.connectTimeout = TIMEOUT_MS
    connection.readTimeout = TIMEOUT_MS
    connection.useCaches = false
    try {
        return connection.responseCode
    } finally {
        connection.disconnect()
    }
}

private fun failure(e: Exception) = ServerCheckResult.Failure(e.message ?: e.javaClass.simpleName)
