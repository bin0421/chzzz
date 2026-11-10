package com.chzzz.recording

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import com.chzzz.TestcontainersConfiguration
import com.chzzz.member.signUp
import org.springframework.mock.web.MockHttpSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doThrow
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class RecordingUploadApiTest {

    companion object {
        /** 테스트 전용 업로드 폴더. 시작 시 생성되는지 보려고 아직 없는 경로를 넘긴다. */
        private val storageDir: Path = createTempDirectory("chzzz-upload-test").resolve("uploads")

        @JvmStatic
        @DynamicPropertySource
        fun storageProperties(registry: DynamicPropertyRegistry) {
            registry.add("recording.storage-dir") { storageDir.toString() }
            registry.add("recording.max-upload-size") { "1KB" }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    /** 녹화방송 등록은 로그인이 필요하다. */
    lateinit var member: MockHttpSession

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @MockitoSpyBean
    lateinit var recordingRepository: RecordingRepository

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording RESTART IDENTITY")
        if (Files.exists(storageDir)) storageDir.listDirectoryEntries().forEach(Files::delete)
        member = mockMvc.signUp("recorder", "녹화방송회원")
    }

    @Test
    fun `업로드 파일로 등록하면 201이고 파일은 UUID 이름으로 저장되며 DB에는 그 이름만 남는다`() {
        val content = ByteArray(64) { it.toByte() }

        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            file(MockMultipartFile("file", "my video.MP4", "video/mp4", content))
            param("title", "업로드 방송")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.title") { value("업로드 방송") }
            jsonPath("$.sourceType") { value("UPLOADED_FILE") }
        }

        val stored = storageDir.listDirectoryEntries()
        assertThat(stored).hasSize(1)
        assertThat(stored[0].name).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.mp4")
        assertThat(stored[0].readBytes()).isEqualTo(content)
        val sourceValue = jdbcTemplate.queryForObject("SELECT source_value FROM recording", String::class.java)
        assertThat(sourceValue).isEqualTo(stored[0].name)
    }

    @Test
    fun `업로드 파일 녹화방송의 상세 videoUrl은 video 엔드포인트다`() {
        val location = upload("clip.webm", ByteArray(16))
        val id = location.substringAfterLast('/')

        mockMvc.get(location).andExpect {
            status { isOk() }
            jsonPath("$.sourceType") { value("UPLOADED_FILE") }
            jsonPath("$.videoUrl") { value("/api/recordings/$id/video") }
        }
    }

    @Test
    fun `mp4나 webm이 아닌 파일은 400 Problem Details로 file 오류를 돌려주고 저장하지 않는다`() {
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            file(MockMultipartFile("file", "clip.avi", "video/x-msvideo", ByteArray(16)))
            param("title", "형식이 틀린 방송")
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.errors[?(@.field == 'file')].message") { value("mp4 또는 webm 파일만 올릴 수 있습니다.") }
        }

        assertThat(storageDir.listDirectoryEntries()).isEmpty()
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM recording", Int::class.java)).isZero()
    }

    @Test
    fun `메타데이터 저장이 실패하면 방금 저장한 파일을 지운다`() {
        // DB 장애는 HTTP 요청만으로 만들 수 없어서, 저장 단계만 실패시킨다.
        doThrow(DataAccessResourceFailureException("DB 연결 끊김"))
            .`when`(recordingRepository).save(ArgumentMatchers.any(Recording::class.java))

        runCatching {
            mockMvc.multipart("/api/recordings") {
                with(csrf())
                session = member
                file(MockMultipartFile("file", "clip.webm", "video/webm", ByteArray(16)))
                param("title", "저장 실패 방송")
            }.andReturn()
        }

        assertThat(storageDir.listDirectoryEntries()).isEmpty()
    }

    @Test
    fun `video는 Range 없이 요청하면 200과 전체 파일을 돌려준다`() {
        val content = ByteArray(100) { it.toByte() }
        val location = upload("clip.webm", content)

        mockMvc.get("$location/video").andExpect {
            status { isOk() }
            header { string("Accept-Ranges", "bytes") }
            content { contentType("video/webm") }
            content { bytes(content) }
        }
    }

    @Test
    fun `video는 Range 요청에 206과 Content-Range, 요청한 바이트만 돌려준다`() {
        val content = ByteArray(100) { it.toByte() }
        val location = upload("clip.mp4", content)

        mockMvc.get("$location/video") {
            header("Range", "bytes=10-19")
        }.andExpect {
            status { isPartialContent() }
            header { string("Accept-Ranges", "bytes") }
            header { string("Content-Range", "bytes 10-19/100") }
            content { contentType("video/mp4") }
            content { bytes(content.copyOfRange(10, 20)) }
        }
    }

    @Test
    fun `video는 파일 크기를 벗어난 Range 요청에 416을 돌려준다`() {
        val location = upload("clip.mp4", ByteArray(100))

        mockMvc.get("$location/video") {
            header("Range", "bytes=100-")
        }.andExpect {
            status { isRequestedRangeNotSatisfiable() }
            header { string("Content-Range", "bytes */100") }
        }
    }

    @Test
    fun `없는 녹화방송의 video는 404다`() {
        mockMvc.get("/api/recordings/999/video").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `외부 URL 녹화방송의 video는 404다`() {
        val location = mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            param("title", "외부 방송")
            param("url", "https://cdn.example.com/external.mp4")
        }.andExpect { status { isCreated() } }
            .andReturn().response.getHeader("Location")!!

        mockMvc.get("$location/video").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `업로드 파일이 디스크에 없으면 video는 404다`() {
        val location = upload("clip.mp4", ByteArray(16))
        storageDir.listDirectoryEntries().forEach(Files::delete)

        mockMvc.get("$location/video").andExpect {
            status { isNotFound() }
        }
    }

    private fun upload(filename: String, content: ByteArray): String =
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            file(MockMultipartFile("file", filename, "application/octet-stream", content))
            param("title", "업로드 방송")
        }.andExpect { status { isCreated() } }
            .andReturn().response.getHeader("Location")!!
}
