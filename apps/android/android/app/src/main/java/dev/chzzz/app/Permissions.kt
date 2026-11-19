package dev.chzzz.app

// WebView 권한 요청 판단. 데스크톱 apps/desktop/src/permissions.ts와 같은 규칙이다:
// 카메라·마이크만, 저장한 서버 출처에서 요청할 때만 허용하고 나머지는 모두 거절한다.
// 허용하면 앱이 Android 앱 권한(CAMERA·RECORD_AUDIO)을 런타임에 요청한 뒤 WebView에 준다.
const val VIDEO_CAPTURE = "android.webkit.resource.VIDEO_CAPTURE"
const val AUDIO_CAPTURE = "android.webkit.resource.AUDIO_CAPTURE"

private val ALLOWED_FOR_SERVER = setOf(VIDEO_CAPTURE, AUDIO_CAPTURE)

fun isWebViewPermissionAllowed(resources: List<String>, requestingOrigin: String?, serverOrigin: String?): Boolean {
    if (serverOrigin == null || resources.isEmpty() || !ALLOWED_FOR_SERVER.containsAll(resources)) return false
    return requestingOrigin != null && originOf(requestingOrigin) == serverOrigin
}

// WebView 자원 → 런타임에 물을 Android 앱 권한. 사용자가 앱 권한을 거절하면 WebView에도 주지 않는다.
private val ANDROID_PERMISSIONS = linkedMapOf(
    VIDEO_CAPTURE to "android.permission.CAMERA",
    AUDIO_CAPTURE to "android.permission.RECORD_AUDIO",
)

fun androidPermissionsFor(resources: List<String>): List<String> =
    ANDROID_PERMISSIONS.filterKeys { it in resources }.values.toList()
