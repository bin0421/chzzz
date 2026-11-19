package dev.chzzz.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import com.getcapacitor.Bridge
import java.util.concurrent.ConcurrentHashMap

// 앱 창 하나(Capacitor WebView)를 시작 화면과 서버 화면 사이에서 움직인다. 데스크톱 main.ts가 하는 일을 Android로 옮겼다.
// 판단은 규칙 함수(decideCertificateTrust, decideLinkTarget, isWebViewPermissionAllowed, persistentLoginCookie)가 하고,
// 여기서는 그 결과를 WebView·Intent에 연결한다.
class AppController(private val activity: MainActivity, private val bridge: Bridge) {
    val settings = AppSettings(activity)
    private val webView: WebView = bridge.webView
    private val shellUrl: String = bridge.localUrl
    private val shellOrigin: String? = originOf(shellUrl)

    // 서버 화면을 열다 만난, 아직 신뢰하지 않은 인증서. 지문 확인 화면(#trust)이 읽는다.
    @Volatile var pendingCertificate: TrustDecision.Ask? = null
        private set

    // 사용자에게 보여 준 (출처 → 지문). 시작 화면이 신뢰하겠다고 보낸 값이 실제로 본 인증서인지 확인한다.
    private val shownFingerprints = ConcurrentHashMap<String, String>()

    // WebView가 지금 띄운(띄우는 중인) 주소. 다리 요청이 시작 화면에서 왔는지 보는 데 쓴다.
    @Volatile var currentUrl: String = shellUrl

    // 서버 화면을 새로 열면 그 화면을 첫 화면으로 삼아 뒤로 가기 기록을 지운다.
    @Volatile var clearHistoryWhenServerLoads = false
    private var shellLoads = 0

    // 서버 화면 열기 시도 번호와, 그 시도가 응답을 받아 화면이 열리기 시작했는지.
    // 닿지 않는 주소(PC 꺼짐, 다른 Wi-Fi)는 연결 시간 초과까지 2분 넘게 빈 화면이라 앱이 먼저 끊는다.
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serverLoad = 0
    private var serverPageStarted = false

    fun start() {
        webView.settings.setSupportMultipleWindows(true)
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        bridge.setWebViewClient(AppWebViewClient(bridge, this))
        webView.webChromeClient = AppChromeClient(bridge, activity, this)
        if (settings.serverOrigin != null) openServer()
    }

    fun isShowingShell(): Boolean = shellOrigin != null && originOf(currentUrl) == shellOrigin

    fun openServer() = activity.runOnUiThread {
        val origin = settings.serverOrigin
        if (origin == null) {
            openShell("setup")
        } else {
            clearHistoryWhenServerLoads = true
            val load = ++serverLoad
            serverPageStarted = false
            // 캐시에 남은 화면을 그대로 띄우지 않도록 서버에 다시 확인한다. 서버가 닿지 않으면 연결 실패 화면으로 간다.
            webView.loadUrl(origin, mapOf("Cache-Control" to "no-cache"))
            mainHandler.postDelayed({
                if (load == serverLoad && !serverPageStarted) {
                    webView.stopLoading()
                    openShell("failed")
                }
            }, SERVER_LOAD_TIMEOUT_MS)
        }
    }

    // 서버 화면이 응답을 받아 열리기 시작했다(WebView onPageStarted). 시간 초과로 끊지 않는다.
    fun onServerPageStarted() {
        serverPageStarted = true
    }

    fun openShell(mode: String) = activity.runOnUiThread {
        serverLoad++
        if (mode != "trust") pendingCertificate = null
        // 같은 문서 안의 # 이동이면 시작 화면이 다시 그려지지 않으므로 매번 다른 주소로 연다.
        webView.loadUrl("$shellUrl/?open=${++shellLoads}#$mode")
    }

    fun rememberShown(ask: TrustDecision.Ask) {
        shownFingerprints[ask.origin] = ask.fingerprint
    }

    fun askToTrust(ask: TrustDecision.Ask) {
        val alreadyShown = pendingCertificate == ask && currentUrl.endsWith("#trust")
        pendingCertificate = ask
        rememberShown(ask)
        if (!alreadyShown) openShell("trust")
    }

    fun trust(origin: String, fingerprint: String?): Boolean {
        if (fingerprint == null || shownFingerprints[origin] != fingerprint) return false
        settings.trust(origin, fingerprint)
        pendingCertificate = null
        return true
    }

    // 뒤로 가기: 서버 화면 안에서는 이전 화면으로, 첫 화면·시작 화면에서는 앱을 닫는다.
    fun back() {
        if (!isShowingShell() && webView.canGoBack()) webView.goBack() else activity.finish()
    }

    // 앱을 다시 열어도 로그인이 유지되도록 서버의 로그인 쿠키에 만료를 붙여 디스크에 남긴다.
    fun keepLoginCookie() {
        val origin = settings.serverOrigin ?: return
        val cookies = CookieManager.getInstance()
        persistentLoginCookie(cookies.getCookie(origin), origin)?.let { cookies.setCookie(origin, it) }
        cookies.flush()
    }

    // 주소 열기 규칙대로 연다. 앱 안에서 열 주소면 true(호출한 쪽이 WebView에서 이어 연다).
    fun openByRule(url: String): Boolean {
        when (decideLinkTarget(url = url, serverOrigin = settings.serverOrigin, shellOrigin = shellOrigin)) {
            LinkTarget.APP -> return true
            LinkTarget.BROWSER -> startView(Uri.parse(url))
            LinkTarget.SYSTEM -> startSystem(url)
            LinkTarget.BLOCK -> Unit
        }
        return false
    }

    private fun startView(uri: Uri): Boolean = start(Intent(Intent.ACTION_VIEW, uri))

    // intent://는 다른 앱(결제 앱 등)을 부르는 주소다. 앱 내부 화면을 직접 열지 못하게 정리한 뒤 넘기고,
    // 그 앱이 없으면 대체 주소(browser_fallback_url)나 스토어 설치 화면으로 보낸다.
    private fun startSystem(url: String) {
        if (!url.startsWith("intent:", ignoreCase = true)) {
            if (!startView(Uri.parse(url))) notice(NO_APP)
            return
        }
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (_: Exception) {
            return
        }
        intent.component = null
        intent.selector = null
        if (start(intent)) return

        val fallback = intent.getStringExtra("browser_fallback_url")
        val fallbackTarget = fallback?.let { decideLinkTarget(it, settings.serverOrigin, shellOrigin) }
        val packageName = intent.getPackage()
        when {
            fallback != null && fallbackTarget == LinkTarget.APP -> webView.loadUrl(fallback)
            fallback != null && fallbackTarget == LinkTarget.BROWSER -> startView(Uri.parse(fallback))
            packageName != null -> if (!startView(Uri.parse("market://details?id=$packageName"))) notice(NO_APP)
            else -> notice(NO_APP)
        }
    }

    private fun start(intent: Intent): Boolean {
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    // 카메라·마이크 앱 권한을 거절했을 때: 다시 허용하는 곳(휴대폰 설정의 앱 권한)을 알려 주고 바로 열 수 있게 한다.
    fun showPermissionSettingsGuide() = activity.runOnUiThread {
        if (activity.isFinishing) return@runOnUiThread
        AlertDialog.Builder(activity)
            .setTitle("카메라·마이크 권한이 꺼져 있습니다")
            .setMessage("라이브 송출에는 카메라와 마이크 권한이 필요합니다. 휴대폰 설정 > 애플리케이션 > chzzz > 권한에서 카메라와 마이크를 허용한 뒤 송출 화면을 다시 열어 주세요.")
            .setPositiveButton("설정 열기") { _, _ ->
                val settings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
                try {
                    activity.startActivity(settings)
                } catch (_: ActivityNotFoundException) {
                    notice("휴대폰 설정에서 chzzz 앱 권한을 허용해 주세요.")
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun notice(message: String) = activity.runOnUiThread {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val NO_APP = "이 주소를 열 앱이 없습니다."
        const val SERVER_LOAD_TIMEOUT_MS = 15_000L
    }
}
