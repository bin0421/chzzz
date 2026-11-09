package com.chzzz.live

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

/**
 * 재시작 정리: 지난 실행에서 대기·방송 중으로 남은 라이브 방송은 서비스가 시작될 때 종료로 정리된다.
 * 앱 컨텍스트가 뜨기 전에 DB에 "지난 실행의 흔적"을 심어 두고, 앱이 뜬 뒤 API로 확인한다.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class LiveBroadcastStartupCleanupApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `시작할 때 대기와 방송 중으로 남은 라이브 방송을 종료로 정리한다`() {
        for (id in listOf(1, 2)) {
            mockMvc.get("/api/live-broadcasts/$id").andExpect {
                jsonPath("$.status") { value("ENDED") }
                jsonPath("$.endedAt") { isString() }
            }
        }
        mockMvc.get("/api/live-broadcasts/3").andExpect {
            jsonPath("$.status") { value("ENDED") }
            jsonPath("$.endedAt") { value("2026-11-09T08:30:00Z") }
        }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))

        /** 앱이 뜨기 전(지난 실행)의 상태: 대기 1, 방송 중 2, 이미 종료 3. */
        @BeforeAll
        @JvmStatic
        fun leftoversFromPreviousRun() {
            Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password).load().migrate()
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use {
                    it.execute(
                        """
                        INSERT INTO live_broadcast (id, title, status, stream_key_hash, created_at, started_at, ended_at) VALUES
                          (1, '남은 대기', 'PENDING', repeat('a', 64), '2026-11-09T08:00:00Z', NULL, NULL),
                          (2, '남은 방송 중', 'LIVE', repeat('b', 64), '2026-11-09T08:00:00Z', '2026-11-09T08:00:05Z', NULL),
                          (3, '이미 종료', 'ENDED', repeat('c', 64), '2026-11-09T08:00:00Z', '2026-11-09T08:00:05Z', '2026-11-09T08:30:00Z')
                        """.trimIndent()
                    )
                }
            }
        }
    }
}
