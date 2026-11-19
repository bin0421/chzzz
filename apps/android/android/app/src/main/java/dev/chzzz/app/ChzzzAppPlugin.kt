package dev.chzzz.app

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONObject

// 시작 화면(apps/shell)이 쓰는 앱 다리. 데스크톱 preload의 chzzzApp과 같은 여섯 가지 일을 한다.
// 시작 화면 쪽 어댑터는 apps/shell/src/capacitorBridge.ts. Capacitor 메서드는 백그라운드 스레드에서 돈다.
@CapacitorPlugin(name = "ChzzzApp")
class ChzzzAppPlugin : Plugin() {
    private val controller: AppController
        get() = (activity as MainActivity).controller

    @PluginMethod
    fun getServerAddress(call: PluginCall) = fromShell(call) {
        call.resolve(JSObject().put("origin", controller.settings.serverOrigin ?: JSONObject.NULL))
    }

    @PluginMethod
    fun checkServer(call: PluginCall) = fromShell(call) {
        val origin = validServerOrigin(call.getString("origin")) ?: return@fromShell call.reject("서버 주소가 올바르지 않습니다.")
        val result = when (val checked = checkServer(origin, controller.settings.trustedCertificates)) {
            is ServerCheckResult.Status -> JSObject().put("status", checked.code)
            is ServerCheckResult.Failure -> JSObject().put("error", checked.message)
            is ServerCheckResult.UntrustedCertificate -> {
                controller.rememberShown(checked.ask)
                JSObject().put("certificate", certificateJson(checked.ask))
            }
        }
        call.resolve(result)
    }

    @PluginMethod
    fun saveServerAndOpen(call: PluginCall) = fromShell(call) {
        val origin = validServerOrigin(call.getString("origin")) ?: return@fromShell call.reject("서버 주소가 올바르지 않습니다.")
        controller.settings.serverOrigin = origin
        controller.openServer()
        call.resolve()
    }

    @PluginMethod
    fun openServer(call: PluginCall) = fromShell(call) {
        controller.openServer()
        call.resolve()
    }

    @PluginMethod
    fun getPendingCertificate(call: PluginCall) = fromShell(call) {
        val pending = controller.pendingCertificate
        call.resolve(JSObject().put("certificate", pending?.let(::certificateJson) ?: JSONObject.NULL))
    }

    @PluginMethod
    fun trustCertificate(call: PluginCall) = fromShell(call) {
        val origin = validServerOrigin(call.getString("origin")) ?: return@fromShell call.reject("서버 주소가 올바르지 않습니다.")
        val fingerprint = call.getString("fingerprint")
        if (!controller.trust(origin, fingerprint)) return@fromShell call.reject("확인한 인증서와 다릅니다.")
        call.resolve()
    }

    // 다리는 시작 화면(앱 내부 출처)이 띄워져 있을 때만 받는다. 서버 화면에는 다리를 열지 않는다.
    private inline fun fromShell(call: PluginCall, block: () -> Unit) {
        if (!controller.isShowingShell()) return call.reject("허용하지 않은 요청입니다.")
        block()
    }

    private fun certificateJson(ask: TrustDecision.Ask) = JSObject()
        .put("origin", ask.origin)
        .put("fingerprint", ask.fingerprint)
        .put("previousFingerprint", ask.previousFingerprint ?: JSONObject.NULL)
}
