package dev.chzzz.app

import android.content.Context

// 서버 주소와 신뢰한 인증서(출처 → 지문)를 앱 데이터 영역(SharedPreferences)에 둔다.
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("chzzz-settings", Context.MODE_PRIVATE)

    var serverOrigin: String?
        get() = prefs.getString(KEY_SERVER, null)
        set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    val trustedCertificates: Map<String, String>
        get() = prefs.all
            .filterKeys { it.startsWith(TRUSTED_PREFIX) }
            .mapNotNull { (key, value) -> (value as? String)?.let { key.removePrefix(TRUSTED_PREFIX) to it } }
            .toMap()

    fun trust(origin: String, fingerprint: String) {
        prefs.edit().putString(TRUSTED_PREFIX + origin, fingerprint).apply()
    }

    private companion object {
        const val KEY_SERVER = "serverOrigin"
        const val TRUSTED_PREFIX = "trusted:"
    }
}
