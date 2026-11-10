package com.chzzz.comment

import com.chzzz.TestcontainersConfiguration
import com.chzzz.member.signUp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

/** 댓글 수정·삭제: 비로그인 401, 작성자 2xx, 다른 회원(방송 소유자 포함) 403. 거절 뒤에는 댓글이 그대로다. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class CommentAuthorApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var owner: MockHttpSession
    private lateinit var author: MockHttpSession
    private lateinit var other: MockHttpSession
    private var recordingId: Long = 0
    private var commentId: Long = 0

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording RESTART IDENTITY")
        owner = mockMvc.signUp("owner", "방송주인")
        author = mockMvc.signUp("author", "작성자")
        other = mockMvc.signUp("other", "다른회원")

        recordingId = mockMvc.multipart("/api/recordings") {
            session = owner
            with(csrf())
            param("title", "주인의 방송")
            param("url", "https://cdn.example.com/a.mp4")
        }.andReturn().response.contentAsString.let { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1].toLong() }

        commentId = mockMvc.post("/api/recordings/$recordingId/comments") {
            session = author
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"원래 댓글"}"""
        }.andReturn().response.contentAsString.let { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1].toLong() }
    }

    @Test
    fun `작성자는 댓글을 수정하고 수정 시각이 채워진다`() {
        mockMvc.patch("/api/comments/$commentId") {
            session = author
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":" 고친 댓글 "}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(commentId) }
            jsonPath("$.content") { value("고친 댓글") }
            jsonPath("$.updatedAt") { isString() }
        }

        mockMvc.get("/api/recordings/$recordingId/comments").andExpect {
            jsonPath("$[0].content") { value("고친 댓글") }
            jsonPath("$[0].updatedAt") { isString() }
        }
    }

    @Test
    fun `댓글 수정은 비로그인 401, 다른 회원과 방송 소유자 403이고 댓글은 그대로다`() {
        patchComment(null).andExpect {
            status { isUnauthorized() }
            content { contentType("application/problem+json") }
        }
        patchComment(other).andExpect {
            status { isForbidden() }
            content { contentType("application/problem+json") }
        }
        patchComment(owner).andExpect { status { isForbidden() } }

        expectOriginalCommentUnchanged()
    }

    @Test
    fun `없는 댓글 수정은 404이고 내용 규칙 위반은 400이다`() {
        mockMvc.patch("/api/comments/999") {
            session = author
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"고침"}"""
        }.andExpect { status { isNotFound() } }

        mockMvc.patch("/api/comments/$commentId") {
            session = author
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"   "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("content") }
        }
        expectOriginalCommentUnchanged()
    }

    @Test
    fun `작성자는 댓글을 삭제한다`() {
        mockMvc.delete("/api/comments/$commentId") {
            session = author
            with(csrf())
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/recordings/$recordingId/comments").andExpect {
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `댓글 삭제는 비로그인 401, 다른 회원과 방송 소유자 403, 없는 댓글 404이고 댓글은 그대로다`() {
        deleteComment(null).andExpect { status { isUnauthorized() } }
        deleteComment(other).andExpect { status { isForbidden() } }
        deleteComment(owner).andExpect { status { isForbidden() } }
        mockMvc.delete("/api/comments/999") {
            session = author
            with(csrf())
        }.andExpect { status { isNotFound() } }

        expectOriginalCommentUnchanged()
    }

    @Test
    fun `CSRF 토큰 없는 댓글 작성·수정·삭제는 403이고 댓글은 그대로다`() {
        mockMvc.post("/api/recordings/$recordingId/comments") {
            session = author
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"위조"}"""
        }.andExpect { status { isForbidden() } }
        mockMvc.patch("/api/comments/$commentId") {
            session = author
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"위조"}"""
        }.andExpect { status { isForbidden() } }
        mockMvc.delete("/api/comments/$commentId") {
            session = author
        }.andExpect { status { isForbidden() } }

        expectOriginalCommentUnchanged()
    }

    private fun patchComment(session: MockHttpSession?) =
        mockMvc.patch("/api/comments/$commentId") {
            if (session != null) this.session = session
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"남이 고침"}"""
        }

    private fun deleteComment(session: MockHttpSession?) =
        mockMvc.delete("/api/comments/$commentId") {
            if (session != null) this.session = session
            with(csrf())
        }

    private fun expectOriginalCommentUnchanged() {
        mockMvc.get("/api/recordings/$recordingId/comments").andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].content") { value("원래 댓글") }
            jsonPath("$[0].updatedAt") { value(null as Any?) }
        }
    }
}
