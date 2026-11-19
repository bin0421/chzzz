package dev.chzzz.app

import android.graphics.Bitmap
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.getcapacitor.Bridge
import com.getcapacitor.BridgeWebViewClient

// 이동·인증서 오류·불러오기 실패를 앱 규칙에 연결한다. 시작 화면 파일 제공은 Capacitor(BridgeWebViewClient)가 그대로 맡는다.
class AppWebViewClient(bridge: Bridge, private val app: AppController) : BridgeWebViewClient(bridge) {

    // 주소 열기 규칙은 최상위 이동(링크·리다이렉트)에 적용한다. 프레임 안 http(s)는 막지 않고(결제위젯 iframe, reCAPTCHA),
    // 프레임에서 부른 다른 앱 주소(intent://, 카드사 앱 스킴)는 규칙대로 운영체제로 넘기고 프레임은 이동하지 않는다.
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme?.lowercase()
        if (!request.isForMainFrame && (scheme == "http" || scheme == "https")) return false
        val openHere = app.openByRule(request.url.toString())
        return !(openHere && request.isForMainFrame)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        app.currentUrl = url
        if (originOf(url) == app.settings.serverOrigin) app.onServerPageStarted()
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        app.currentUrl = url
        if (originOf(url) == app.settings.serverOrigin) {
            if (app.clearHistoryWhenServerLoads) {
                app.clearHistoryWhenServerLoads = false
                view.clearHistory()
            }
            app.keepLoginCookie()
        }
    }

    // 신뢰한 인증서 판단(ADR-0013): 저장한 서버 출처에서 신뢰한 지문이면 이어서 연결하고, 처음 보거나 바뀐 지문이면
    // 연결하지 않고 지문 확인 화면을 연다. 다른 출처의 인증서 오류는 묻지 않고 거절한다.
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val der = derOf(error.certificate)
        if (der == null) {
            handler.cancel()
            return
        }
        val decision = decideCertificateTrust(
            url = error.url,
            fingerprint = fingerprintOfDer(der),
            serverOrigin = app.settings.serverOrigin,
            trusted = app.settings.trustedCertificates,
        )
        when (decision) {
            TrustDecision.Accept -> handler.proceed()
            TrustDecision.Reject -> handler.cancel()
            is TrustDecision.Ask -> {
                handler.cancel()
                app.askToTrust(decision)
            }
        }
    }

    // 서버 화면을 열지 못하면(서버 꺼짐, 닿지 않음) 연결 실패 화면을 보여 준다. 인증서 오류는 지문 확인 화면이 맡는다.
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        if (originOf(request.url.toString()) != app.settings.serverOrigin) return
        if (error.errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE && app.pendingCertificate != null) return
        app.openShell("failed")
    }

    private fun derOf(certificate: SslCertificate): ByteArray? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            certificate.x509Certificate?.encoded
        } else {
            // Android 10 미만은 공개 API가 없어, 플랫폼이 인증서를 저장하는 키로 DER를 꺼낸다.
            SslCertificate.saveState(certificate).getByteArray("x509-certificate")
        }
}
