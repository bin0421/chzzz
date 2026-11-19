package dev.chzzz.app

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.core.content.ContextCompat
import android.os.Message
import android.webkit.PermissionRequest
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.getcapacitor.Bridge
import com.getcapacitor.BridgeWebChromeClient

class AppChromeClient(
    bridge: Bridge,
    private val activity: ComponentActivity,
    private val app: AppController,
) : BridgeWebChromeClient(bridge) {

    // 앱 권한을 묻는 동안 기다리는 WebView 요청. 한 번에 하나만 묻는다.
    private var waiting: PermissionRequest? = null
    private val askAndroidPermissions = activity.registerForActivityResult(RequestMultiplePermissions()) { results ->
        val request = waiting ?: return@registerForActivityResult
        waiting = null
        if (results.values.all { it }) {
            request.grant(request.resources)
        } else {
            request.deny()
            app.showPermissionSettingsGuide()
        }
    }

    // 카메라·마이크 요청은 저장한 서버 출처에서만 받는다. 받으면 앱 권한(CAMERA·RECORD_AUDIO)을 런타임에 묻고,
    // 사용자가 허용해야 WebView에 준다. 거절하면 화면의 getUserMedia가 NotAllowedError로 실패하고,
    // 앱이 휴대폰 설정에서 다시 허용하는 방법을 안내한다(화면 문구는 브라우저 기준이라서).
    override fun onPermissionRequest(request: PermissionRequest) {
        val resources = request.resources.toList()
        val allowed = isWebViewPermissionAllowed(
            resources = resources,
            requestingOrigin = request.origin?.toString(),
            serverOrigin = app.settings.serverOrigin,
        )
        if (!allowed) return request.deny()

        val missing = androidPermissionsFor(resources).filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return request.grant(request.resources)
        waiting?.deny()
        waiting = request
        askAndroidPermissions.launch(missing.toTypedArray())
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        if (waiting == request) waiting = null
    }

    // 새 창(window.open, target=_blank)도 주소 열기 규칙을 따른다. 앱 창은 하나라서 앱 안에서 열 주소는 지금 창에서 연다.
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        val popup = WebView(view.context)
        popup.webViewClient = object : WebViewClient() {
            private var handled = false

            override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                open(request.url.toString())
                return true
            }

            override fun onPageStarted(popupView: WebView, url: String, favicon: Bitmap?) {
                popupView.stopLoading()
                open(url)
            }

            private fun open(url: String) {
                if (handled || url == "about:blank") return
                handled = true
                if (app.openByRule(url)) view.loadUrl(url)
                popup.post { popup.destroy() }
            }
        }
        (resultMsg.obj as WebView.WebViewTransport).webView = popup
        resultMsg.sendToTarget()
        return true
    }
}
