package com.chzzz

import com.chzzz.member.TEST_PASSWORD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files

/**
 * 5단계: 백엔드가 여러 대여도 같은 결과를 낸다.
 * 같은 PostgreSQL을 쓰는 애플리케이션 두 개(인스턴스 A·B)를 실제 서버로 띄우고,
 * 브라우저 하나가 같은 쿠키로 두 인스턴스를 오가는 상황(로그인 세션 공유, ADR-0009)과
 * 인스턴스마다 도는 주기 작업이 겹치는 상황을 흉내 낸다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiInstanceApiTest {

    companion object {
        const val INSTANCE_HEADER = "X-Backend-Instance"
    }

    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
    private lateinit var instanceA: ConfigurableApplicationContext
    private lateinit var instanceB: ConfigurableApplicationContext

    private val portA get() = instanceA.environment.getRequiredProperty("local.server.port").toInt()
    private val portB get() = instanceB.environment.getRequiredProperty("local.server.port").toInt()

    @BeforeAll
    fun startInstances() {
        postgres.start()
        val storageDir = Files.createTempDirectory("shared-session-uploads")
        instanceA = startInstance("instance-a", storageDir.toString())
        instanceB = startInstance("instance-b", storageDir.toString())
    }

    @AfterAll
    fun stopInstances() {
        if (::instanceA.isInitialized) instanceA.close()
        if (::instanceB.isInitialized) instanceB.close()
        postgres.stop()
    }

    private fun startInstance(name: String, storageDir: String): ConfigurableApplicationContext =
        // 명령줄 인자는 application.properties보다 우선한다(`properties(...)`는 기본값이라 밀린다).
        SpringApplicationBuilder(BackendApplication::class.java).run(
            "--server.port=0",
            "--spring.datasource.url=${postgres.jdbcUrl}",
            "--spring.datasource.username=${postgres.username}",
            "--spring.datasource.password=${postgres.password}",
            "--recording.storage-dir=$storageDir",
            "--instance.name=$name",
        )

    @Test
    fun `A에서 로그인한 세션 쿠키로 B에서도 로그인 상태다`() {
        val onA = BrowserLikeClient(portA)
        onA.signUp("shared_me", "공유회원")

        val onB = onA.onPort(portB)

        assertThat(onB.get("/api/auth/me")).isEqualTo(200)
    }

    @Test
    fun `A에서 로그아웃하면 B에서도 로그아웃 상태다`() {
        val onA = BrowserLikeClient(portA)
        onA.signUp("shared_logout", "로그아웃회원")
        val onB = onA.onPort(portB)
        assertThat(onB.get("/api/auth/me")).isEqualTo(200)
        // 로그아웃 응답이 세션 쿠키를 지우더라도, 지워지기 전 쿠키로 B에 가도 로그아웃이어야 한다.
        val sessionBeforeLogout = onA.cookies.toMap()

        val logoutStatus = onA.post("/api/auth/logout", {}) { status, _ -> status }

        assertThat(logoutStatus).isEqualTo(204)
        assertThat(onB.get("/api/auth/me")).isEqualTo(401)
        assertThat(BrowserLikeClient(portB, sessionBeforeLogout.toMutableMap()).get("/api/auth/me")).isEqualTo(401)
    }

    @Test
    fun `A에서 로그인한 직후 B에서 CSRF가 필요한 댓글 작성이 통과한다`() {
        val onA = BrowserLikeClient(portA)
        onA.signUp("shared_comment", "댓글회원")
        val recordingLocation = onA.post(
            "/api/recordings",
            {
                contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("title=shared-recording&url=https://cdn.example.com/shared.mp4")
            },
        ) { status, headers ->
            assertThat(status).isEqualTo(201)
            headers.getFirst(HttpHeaders.LOCATION)!!
        }
        val onB = onA.onPort(portB)

        val commentStatus = onB.post(
            "$recordingLocation/comments",
            { contentType(MediaType.APPLICATION_JSON).body("""{"content":"B에서 쓴 댓글"}""") },
        ) { status, _ -> status }

        assertThat(commentStatus).isEqualTo(201)
    }

    @Test
    fun `응답마다 처리한 인스턴스 이름이 헤더에 실린다`() {
        val onA = BrowserLikeClient(portA)
        val onB = onA.onPort(portB)

        onA.get("/api/recordings")
        val fromA = onA.lastResponseHeaders.getFirst(INSTANCE_HEADER)
        // 로그인이 필요해 401로 끝나는 응답(보안 필터가 쓴 응답)에도 실린다.
        onB.get("/api/auth/me")
        val fromB = onB.lastResponseHeaders.getFirst(INSTANCE_HEADER)

        assertThat(fromA).isEqualTo("instance-a")
        assertThat(fromB).isEqualTo("instance-b")
    }

    private fun BrowserLikeClient.signUp(loginId: String, nickname: String) {
        val status = post(
            "/api/auth/signup",
            {
                contentType(MediaType.APPLICATION_JSON)
                    .body("""{"loginId":"$loginId","password":"$TEST_PASSWORD","nickname":"$nickname"}""")
            },
        ) { status, _ -> status }
        assertThat(status).isEqualTo(201)
    }
}
