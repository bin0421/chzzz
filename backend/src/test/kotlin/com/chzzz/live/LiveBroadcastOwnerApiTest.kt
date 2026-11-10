package com.chzzz.live

import com.chzzz.TestcontainersConfiguration
import com.chzzz.member.signUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** 라이브 방송 시작에는 로그인이 필요하고, 시작한 회원이 소유자가 된다. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class LiveBroadcastOwnerApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE live_broadcast RESTART IDENTITY")
    }

    @Test
    fun `로그인하지 않고 라이브 방송을 시작하면 401이고 만들어지지 않는다`() {
        mockMvc.post("/api/live-broadcasts") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"몰래 시작"}"""
        }.andExpect {
            status { isUnauthorized() }
            content { contentType("application/problem+json") }
        }

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM live_broadcast", Int::class.java)).isZero()
    }

    @Test
    fun `로그인한 회원이 시작하면 소유자가 되고 시작·목록·상세에 소유자 닉네임이 나온다`() {
        val member = mockMvc.signUp("caster", "송출주인")

        mockMvc.post("/api/live-broadcasts") {
            session = member
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"내 라이브"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.owner.id") { isNumber() }
            jsonPath("$.owner.nickname") { value("송출주인") }
        }
        mockMvc.post("/internal/mediamtx/available") { param("path", "live/1") }

        mockMvc.get("/api/live-broadcasts").andExpect {
            jsonPath("$[0].owner.nickname") { value("송출주인") }
        }
        mockMvc.get("/api/live-broadcasts/1").andExpect {
            jsonPath("$.owner.nickname") { value("송출주인") }
            jsonPath("$.owner.loginId") { doesNotExist() }
        }
    }

    @Test
    fun `회원 기능 이전에 시작된 라이브 방송은 소유자가 없다`() {
        jdbcTemplate.update(
            "INSERT INTO live_broadcast (title, stream_key_hash, status, created_at, ended_at) " +
                "VALUES ('옛 라이브', ?, 'ENDED', now(), now())",
            "0".repeat(64),
        )

        mockMvc.get("/api/live-broadcasts").andExpect {
            jsonPath("$[0].title") { value("옛 라이브") }
            jsonPath("$[0].owner") { value(null as Any?) }
        }
        mockMvc.get("/api/live-broadcasts/1").andExpect {
            status { isOk() }
            jsonPath("$.owner") { value(null as Any?) }
        }
    }
}
