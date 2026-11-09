package com.chzzz.recording

import com.chzzz.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class RecordingNotFoundApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `없는 녹화방송을 조회하면 404 Problem Details를 돌려준다`() {
        mockMvc.get("/api/recordings/999999").andExpect {
            status { isNotFound() }
            content { contentType("application/problem+json") }
            jsonPath("$.status") { value(404) }
            jsonPath("$.title") { value("찾을 수 없음") }
            jsonPath("$.detail") { value("녹화방송을 찾을 수 없습니다.") }
        }
    }
}
