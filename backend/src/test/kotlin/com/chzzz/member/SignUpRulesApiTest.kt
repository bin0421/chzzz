package com.chzzz.member

import com.chzzz.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class SignUpRulesApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE member RESTART IDENTITY CASCADE")
    }

    @ParameterizedTest(name = "아이디 [{0}] → {1}")
    @CsvSource(
        "'', 아이디를 입력해 주세요.",
        "abc, '아이디는 영문 소문자, 숫자, _로 4~20자여야 합니다.'",
        "Streamer, '아이디는 영문 소문자, 숫자, _로 4~20자여야 합니다.'",
        "stream-er, '아이디는 영문 소문자, 숫자, _로 4~20자여야 합니다.'",
        "a23456789012345678901, '아이디는 영문 소문자, 숫자, _로 4~20자여야 합니다.'",
    )
    fun `아이디 규칙을 어기면 400으로 loginId 오류를 주고 가입되지 않는다`(loginId: String, message: String) {
        signUp(loginId, TEST_PASSWORD, "닉네임").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[?(@.field == 'loginId')].message") { value(message) }
        }
        assertThat(memberCount()).isZero()
    }

    @ParameterizedTest(name = "비밀번호 길이 {0}")
    @CsvSource("0, 비밀번호를 입력해 주세요.", "7, 비밀번호는 8~64자여야 합니다.", "65, 비밀번호는 8~64자여야 합니다.")
    fun `비밀번호 길이 규칙을 어기면 400으로 password 오류를 준다`(length: Int, message: String) {
        signUp("streamer", "x".repeat(length), "닉네임").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[?(@.field == 'password')].message") { value(message) }
        }
        assertThat(memberCount()).isZero()
    }

    @Test
    fun `비밀번호는 8자와 64자 경계에서 가입된다`() {
        signUp("short_pw", "x".repeat(8), "여덟").andExpect { status { isCreated() } }
        signUp("long_pw", "x".repeat(64), "육십사").andExpect { status { isCreated() } }
    }

    @ParameterizedTest(name = "닉네임 [{0}] → {1}")
    @CsvSource(
        "'   ', 닉네임을 입력해 주세요.",
        "가나다라마바사아자차카타파하가나다라마바사, 닉네임은 20자 이하로 입력해 주세요.",
    )
    fun `닉네임 규칙을 어기면 400으로 nickname 오류를 준다`(nickname: String, message: String) {
        signUp("streamer", TEST_PASSWORD, nickname).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[?(@.field == 'nickname')].message") { value(message) }
        }
        assertThat(memberCount()).isZero()
    }

    @Test
    fun `아이디와 닉네임의 앞뒤 공백은 지우고 저장한다`() {
        signUp("  streamer  ", TEST_PASSWORD, "  스트리머  ").andExpect {
            status { isCreated() }
            jsonPath("$.nickname") { value("스트리머") }
        }
        assertThat(jdbcTemplate.queryForObject("SELECT login_id FROM member", String::class.java)).isEqualTo("streamer")
    }

    @Test
    fun `이미 쓰는 아이디로 가입하면 409로 loginId 오류를 준다`() {
        signUp("streamer", TEST_PASSWORD, "첫째").andExpect { status { isCreated() } }

        signUp("streamer", TEST_PASSWORD, "둘째").andExpect {
            status { isConflict() }
            content { contentType("application/problem+json") }
            jsonPath("$.errors[?(@.field == 'loginId')].message") { value("이미 사용 중인 아이디입니다.") }
        }
        assertThat(memberCount()).isEqualTo(1)
    }

    @Test
    fun `대소문자와 앞뒤 공백만 다른 닉네임은 중복이라 409로 nickname 오류를 준다`() {
        signUp("first", TEST_PASSWORD, "Chzzz").andExpect { status { isCreated() } }

        signUp("second", TEST_PASSWORD, " chZZZ ").andExpect {
            status { isConflict() }
            jsonPath("$.errors[?(@.field == 'nickname')].message") { value("이미 사용 중인 닉네임입니다.") }
        }
        assertThat(memberCount()).isEqualTo(1)
    }

    @Test
    fun `비밀번호는 원문이 아니라 해시로 저장된다`() {
        signUp("streamer", TEST_PASSWORD, "스트리머").andExpect { status { isCreated() } }

        val stored = jdbcTemplate.queryForObject("SELECT password_hash FROM member", String::class.java)!!
        assertThat(stored).doesNotContain(TEST_PASSWORD).startsWith("{bcrypt}")
    }

    private fun signUp(loginId: String, password: String, nickname: String): ResultActionsDsl =
        mockMvc.post("/api/auth/signup") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"loginId":"$loginId","password":"$password","nickname":"$nickname"}"""
        }

    private fun memberCount(): Int = jdbcTemplate.queryForObject("SELECT count(*) FROM member", Int::class.java)!!
}
