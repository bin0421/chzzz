package com.chzzz.recording

import com.chzzz.TestcontainersConfiguration
import com.chzzz.member.signUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class RecordingOwnerApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE recording RESTART IDENTITY")
    }

    @Test
    fun `로그인하지 않고 녹화방송을 등록하면 401이고 저장되지 않는다`() {
        mockMvc.multipart("/api/recordings") {
            with(csrf())
            param("title", "몰래 등록")
            param("url", "https://cdn.example.com/a.mp4")
        }.andExpect {
            status { isUnauthorized() }
            content { contentType("application/problem+json") }
        }

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM recording", Int::class.java)).isZero()
    }

    @Test
    fun `로그인한 회원이 등록하면 소유자가 되고 등록·목록·상세에 소유자 닉네임이 나온다`() {
        val member = mockMvc.signUp("owner", "방송주인")

        val location = mockMvc.multipart("/api/recordings") {
            session = member
            with(csrf())
            param("title", "내 방송")
            param("url", "https://cdn.example.com/mine.mp4")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.owner.id") { isNumber() }
            jsonPath("$.owner.nickname") { value("방송주인") }
        }.andReturn().response.getHeader("Location")!!

        mockMvc.get("/api/recordings").andExpect {
            jsonPath("$[0].owner.nickname") { value("방송주인") }
        }
        mockMvc.get(location).andExpect {
            jsonPath("$.owner.nickname") { value("방송주인") }
        }
    }

    @Test
    fun `회원 기능 이전에 등록된 녹화방송은 소유자가 없다`() {
        jdbcTemplate.update(
            "INSERT INTO recording (title, source_type, source_value, created_at) VALUES (?, 'EXTERNAL_URL', ?, now())",
            "옛 방송", "https://cdn.example.com/old.mp4",
        )

        mockMvc.get("/api/recordings").andExpect {
            jsonPath("$[0].title") { value("옛 방송") }
            jsonPath("$[0].owner") { value(null as Any?) }
        }
        mockMvc.get("/api/recordings/1").andExpect {
            status { isOk() }
            jsonPath("$.owner") { value(null as Any?) }
        }
    }
}
