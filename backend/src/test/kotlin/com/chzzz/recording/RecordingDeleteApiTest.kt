package com.chzzz.recording

import com.chzzz.TestcontainersConfiguration
import com.chzzz.common.BroadcastDeletedEvent
import com.chzzz.common.BroadcastType
import com.chzzz.member.signUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class RecordingDeleteApiTest {

    companion object {
        private val storageDir: Path = createTempDirectory("chzzz-delete-test")

        @JvmStatic
        @DynamicPropertySource
        fun storageProperties(registry: DynamicPropertyRegistry) {
            registry.add("recording.storage-dir") { storageDir.toString() }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var events: ApplicationEvents

    lateinit var owner: MockHttpSession

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording RESTART IDENTITY")
        storageDir.listDirectoryEntries().forEach(Files::delete)
        owner = mockMvc.signUp("owner", "방송주인")
    }

    private fun registerUrlAs(session: MockHttpSession): String =
        mockMvc.multipart("/api/recordings") {
            this.session = session
            with(csrf())
            param("title", "지울 방송")
            param("url", "https://cdn.example.com/a.mp4")
        }.andExpect { status { isCreated() } }.andReturn().response.getHeader("Location")!!

    private fun deleteAs(location: String, session: MockHttpSession? = null) =
        mockMvc.delete(location) {
            session?.let { this.session = it }
            with(csrf())
        }

    private fun deletedEvents() = events.stream(BroadcastDeletedEvent::class.java).toList()

    @Test
    fun `소유자가 삭제하면 204이고 이후 상세는 404, 목록에서도 빠지며 방송 삭제됨 이벤트가 발행된다`() {
        val location = registerUrlAs(owner)
        val id = location.substringAfterLast('/').toLong()

        deleteAs(location, owner).andExpect { status { isNoContent() } }

        mockMvc.get(location).andExpect { status { isNotFound() } }
        mockMvc.get("/api/recordings").andExpect { jsonPath("$.length()") { value(0) } }
        assertThat(deletedEvents()).containsExactly(BroadcastDeletedEvent(BroadcastType.RECORDING, id))
    }

    @Test
    fun `업로드 파일 녹화방송을 소유자가 삭제하면 디스크의 파일도 지워지고 영상 주소는 404다`() {
        val location = mockMvc.multipart("/api/recordings") {
            session = owner
            with(csrf())
            file(MockMultipartFile("file", "clip.mp4", "video/mp4", ByteArray(32)))
            param("title", "업로드 방송")
        }.andExpect { status { isCreated() } }.andReturn().response.getHeader("Location")!!
        assertThat(storageDir.listDirectoryEntries()).hasSize(1)

        deleteAs(location, owner).andExpect { status { isNoContent() } }

        assertThat(storageDir.listDirectoryEntries()).isEmpty()
        mockMvc.get("$location/video").andExpect { status { isNotFound() } }
    }

    @Test
    fun `비로그인이 삭제하면 401이고 녹화방송은 남으며 이벤트도 없다`() {
        val location = registerUrlAs(owner)

        deleteAs(location).andExpect {
            status { isUnauthorized() }
            content { contentType("application/problem+json") }
        }

        mockMvc.get(location).andExpect { status { isOk() } }
        assertThat(deletedEvents()).isEmpty()
    }

    @Test
    fun `다른 회원이 삭제하면 403이고 업로드 파일까지 그대로 남는다`() {
        val location = mockMvc.multipart("/api/recordings") {
            session = owner
            with(csrf())
            file(MockMultipartFile("file", "clip.webm", "video/webm", ByteArray(16)))
            param("title", "남의 방송")
        }.andExpect { status { isCreated() } }.andReturn().response.getHeader("Location")!!
        val other = mockMvc.signUp("other", "다른회원")

        deleteAs(location, other).andExpect {
            status { isForbidden() }
            content { contentType("application/problem+json") }
        }

        mockMvc.get(location).andExpect { status { isOk() } }
        mockMvc.get("$location/video").andExpect { status { isOk() } }
        assertThat(storageDir.listDirectoryEntries()).hasSize(1)
        assertThat(deletedEvents()).isEmpty()
    }

    @Test
    fun `없는 녹화방송을 삭제하면 로그인한 회원에게 404, 비로그인에게 401이다`() {
        deleteAs("/api/recordings/999", owner).andExpect { status { isNotFound() } }
        deleteAs("/api/recordings/999").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `소유자 없는 기존 녹화방송은 어떤 회원도 삭제할 수 없다`() {
        jdbcTemplate.update(
            "INSERT INTO recording (title, source_type, source_value, created_at) VALUES (?, 'EXTERNAL_URL', ?, now())",
            "옛 방송", "https://cdn.example.com/old.mp4",
        )

        deleteAs("/api/recordings/1", owner).andExpect { status { isForbidden() } }

        mockMvc.get("/api/recordings/1").andExpect { status { isOk() } }
    }

    @Test
    fun `CSRF 토큰 없이 삭제하면 403이고 녹화방송은 남는다`() {
        val location = registerUrlAs(owner)

        mockMvc.delete(location) { session = owner }.andExpect { status { isForbidden() } }

        mockMvc.get(location).andExpect { status { isOk() } }
    }
}
