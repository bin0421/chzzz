package dev.chzzz.app

import org.junit.Assert.assertEquals
import org.junit.Test

// 데스크톱 권한 허용 판단(01)과 같은 표를 WebView 권한 요청(카메라·마이크 자원)으로 옮긴다.
class PermissionsTest {
    private val server = "https://192.168.0.5:8443"

    private fun allowed(resources: List<String>, origin: String?, serverOrigin: String? = server) =
        isWebViewPermissionAllowed(resources = resources, requestingOrigin = origin, serverOrigin = serverOrigin)

    @Test fun `저장한 서버 출처의 카메라·마이크 요청은 허용한다`() {
        assertEquals(true, allowed(listOf(VIDEO_CAPTURE, AUDIO_CAPTURE), "$server/"))
        assertEquals(true, allowed(listOf(VIDEO_CAPTURE), server))
        assertEquals(true, allowed(listOf(AUDIO_CAPTURE), "$server/live/3/broadcast"))
    }

    @Test fun `다른 출처의 요청은 거절한다`() {
        listOf(
            "https://evil.example.com/",
            "https://192.168.0.5:9443/",
            "http://192.168.0.5:8443/",
            "https://localhost/",
            "https://js.tosspayments.com/",
            "not a url",
            null,
        ).forEach { origin -> assertEquals(origin, false, allowed(listOf(VIDEO_CAPTURE, AUDIO_CAPTURE), origin)) }
    }

    @Test fun `카메라·마이크가 아닌 자원이 섞이면 거절한다`() {
        assertEquals(false, allowed(listOf("android.webkit.resource.PROTECTED_MEDIA_ID"), server))
        assertEquals(false, allowed(listOf(VIDEO_CAPTURE, "android.webkit.resource.MIDI_SYSEX"), server))
        assertEquals(false, allowed(emptyList(), server))
    }

    @Test fun `저장한 서버 주소가 없으면 거절한다`() {
        assertEquals(false, allowed(listOf(VIDEO_CAPTURE), server, serverOrigin = null))
    }
}

// WebView가 요청한 자원에 필요한 Android 앱 권한(런타임에 물을 것).
class AndroidPermissionsTest {
    @Test fun `카메라는 CAMERA, 마이크는 RECORD_AUDIO를 요청한다`() {
        assertEquals(listOf("android.permission.CAMERA"), androidPermissionsFor(listOf(VIDEO_CAPTURE)))
        assertEquals(listOf("android.permission.RECORD_AUDIO"), androidPermissionsFor(listOf(AUDIO_CAPTURE)))
        assertEquals(
            listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"),
            androidPermissionsFor(listOf(VIDEO_CAPTURE, AUDIO_CAPTURE)),
        )
    }

    @Test fun `그 밖의 자원은 앱 권한이 없다`() {
        assertEquals(emptyList<String>(), androidPermissionsFor(listOf("android.webkit.resource.PROTECTED_MEDIA_ID")))
    }
}
