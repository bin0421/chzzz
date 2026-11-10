package com.chzzz.recording

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import com.chzzz.TestcontainersConfiguration
import com.chzzz.member.signUp
import org.springframework.mock.web.MockHttpSession
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class RecordingValidationApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    /** 녹화방송 등록은 로그인이 필요하다. */
    lateinit var member: MockHttpSession

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording RESTART IDENTITY")
        member = mockMvc.signUp("recorder", "녹화방송회원")
    }

    @Test
    fun `제목의 앞뒤 공백을 제거해서 저장한다`() {
        val location = mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            param("title", "  공백 있는 제목 \t")
            param("url", "https://cdn.example.com/trim.mp4")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.title") { value("공백 있는 제목") }
        }.andReturn().response.getHeader("Location")!!

        mockMvc.get(location).andExpect {
            jsonPath("$.title") { value("공백 있는 제목") }
        }
    }

    @Test
    fun `공백 제거 후 제목이 100자를 넘으면 400으로 title 오류를 돌려준다`() {
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            param("title", " " + "가".repeat(101) + " ")
            param("url", "https://cdn.example.com/long.mp4")
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.errors[0].field") { value("title") }
            jsonPath("$.errors[0].message") { value("제목은 100자 이하로 입력해 주세요.") }
        }

        assertNoRecordings()
    }

    @Test
    fun `공백 제거 후 제목이 정확히 100자면 등록된다`() {
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            param("title", " " + "가".repeat(100) + " ")
            param("url", "https://cdn.example.com/max.mp4")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.title") { value("가".repeat(100)) }
        }
    }

    @Test
    fun `파일도 URL도 없으면 400으로 source 오류를 돌려준다`() {
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            param("title", "출처 없는 방송")
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.errors[0].field") { value("source") }
            jsonPath("$.errors[0].message") { value("업로드 파일이나 외부 URL 중 하나를 입력해 주세요.") }
        }

        assertNoRecordings()
    }

    @Test
    fun `파일과 URL이 둘 다 있으면 400으로 source 오류를 돌려준다`() {
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            file(MockMultipartFile("file", "both.mp4", "video/mp4", byteArrayOf(1, 2, 3)))
            param("title", "출처가 둘인 방송")
            param("url", "https://cdn.example.com/both.mp4")
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.errors[0].field") { value("source") }
            jsonPath("$.errors[0].message") { value("업로드 파일과 외부 URL 중 하나만 입력해 주세요.") }
        }

        assertNoRecordings()
    }

    @Test
    fun `URL이 http나 https가 아니면 400으로 url 오류를 돌려준다`() {
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            param("title", "FTP 방송")
            param("url", "ftp://cdn.example.com/video.mp4")
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.errors[0].field") { value("url") }
            jsonPath("$.errors[0].message") { value("http 또는 https로 시작하는 영상 주소를 입력해 주세요.") }
        }

        assertNoRecordings()
    }

    @Test
    fun `URL에 호스트가 없으면 400으로 url 오류를 돌려준다`() {
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            session = member
            param("title", "호스트 없는 방송")
            param("url", "https:///video.mp4")
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.errors[0].field") { value("url") }
            jsonPath("$.errors[0].message") { value("영상 주소에 호스트가 없습니다. 주소를 확인해 주세요.") }
        }

        assertNoRecordings()
    }

    @Test
    fun `같은 제목과 같은 URL로 다시 등록해도 성공한다`() {
        repeat(2) {
            mockMvc.multipart("/api/recordings") {
                with(csrf())
                session = member
                param("title", "같은 이름의 방송")
                param("url", "https://cdn.example.com/same.mp4")
            }.andExpect { status { isCreated() } }
        }

        mockMvc.get("/api/recordings").andExpect {
            jsonPath("$.length()") { value(2) }
        }
    }

    private fun assertNoRecordings() {
        mockMvc.get("/api/recordings").andExpect {
            jsonPath("$.length()") { value(0) }
        }
    }
}
