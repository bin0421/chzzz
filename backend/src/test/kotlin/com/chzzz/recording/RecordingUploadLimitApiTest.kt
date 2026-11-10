package com.chzzz.recording

import com.chzzz.BrowserLikeClient
import com.chzzz.TestcontainersConfiguration
import com.chzzz.member.TEST_PASSWORD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.util.LinkedMultiValueMap
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries

/** MockMvc는 multipart 크기 한도를 적용하지 않으므로 실제 서버에 브라우저처럼 요청한다. 이 테스트의 한도는 1KB다. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecordingUploadLimitApiTest {

    companion object {
        private val storageDir: Path = createTempDirectory("chzzz-upload-limit-test").resolve("uploads")

        @JvmStatic
        @DynamicPropertySource
        fun storageProperties(registry: DynamicPropertyRegistry) {
            registry.add("recording.storage-dir") { storageDir.toString() }
            registry.add("recording.max-upload-size") { "1KB" }
        }
    }

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        if (Files.exists(storageDir)) storageDir.listDirectoryEntries().forEach(Files::delete)
    }

    @Test
    fun `최대 업로드 크기를 넘으면 413 Problem Details를 돌려준다`() {
        val body = LinkedMultiValueMap<String, Any>().apply {
            add("title", "너무 큰 방송")
            add("file", object : ByteArrayResource(ByteArray(2048)) {
                override fun getFilename() = "big.mp4"
            })
        }

        val browser = BrowserLikeClient(port)
        browser.post(
            "/api/auth/signup",
            {
                contentType(MediaType.APPLICATION_JSON)
                    .body("""{"loginId":"uploader","password":"$TEST_PASSWORD","nickname":"업로더"}""")
            },
        ) { status, _ -> assertThat(status).isEqualTo(201) }

        val (status, contentType) = browser.post(
            "/api/recordings",
            { contentType(MediaType.MULTIPART_FORM_DATA).body(body) },
        ) { status, headers -> status to headers.contentType }

        assertThat(status).isEqualTo(413)
        assertThat(contentType.toString()).isEqualTo("application/problem+json")
        assertThat(storageDir.listDirectoryEntries()).isEmpty()
    }
}
