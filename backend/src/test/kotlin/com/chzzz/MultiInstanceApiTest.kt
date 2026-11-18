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
import com.chzzz.live.LivePendingSweeper
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Files
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    @Test
    fun `두 인스턴스가 대기 방송 정리를 동시에 돌려도 예외 없이 같은 결과다`() {
        val jdbc = instanceA.getBean(JdbcTemplate::class.java)
        val createdAt = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MICROS)
        val ids = (1..20).map {
            jdbc.queryForObject(
                "INSERT INTO live_broadcast (title, status, stream_key_hash, created_at) " +
                    "VALUES (?, 'PENDING', ?, ?) RETURNING id",
                Long::class.java, "겹치는 정리 $it", "hash-$it", Timestamp.from(createdAt),
            )!!
        }
        val sweepers = listOf(instanceA, instanceB).map { it.getBean(LivePendingSweeper::class.java) }
        val ready = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)

        val runs = (1..8).map { i ->
            pool.submit {
                ready.await()
                sweepers[i % 2].sweep()
            }
        }
        ready.countDown()
        runs.forEach { it.get(30, TimeUnit.SECONDS) } // 한 번이라도 예외가 나면 여기서 실패한다.
        pool.shutdown()

        val rows = jdbc.queryForList(
            "SELECT status, ended_at FROM live_broadcast WHERE id IN (${ids.joinToString()})",
        )
        val expectedEnd = Timestamp.from(createdAt.plus(Duration.ofSeconds(60)))
        assertThat(rows).hasSize(20).allSatisfy { row ->
            assertThat(row["status"]).isEqualTo("ENDED")
            assertThat(row["ended_at"]).isEqualTo(expectedEnd)
        }
    }

    @Test
    fun `새 인스턴스가 떠도 다른 인스턴스의 대기·방송 중 라이브 방송은 그대로다`() {
        val jdbc = instanceA.getBean(JdbcTemplate::class.java)
        val now = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val pendingId = jdbc.queryForObject(
            "INSERT INTO live_broadcast (title, status, stream_key_hash, created_at) " +
                "VALUES ('A의 대기', 'PENDING', 'hash-pending', ?) RETURNING id",
            Long::class.java, now,
        )!!
        val liveId = jdbc.queryForObject(
            "INSERT INTO live_broadcast (title, status, stream_key_hash, created_at, started_at) " +
                "VALUES ('A의 방송 중', 'LIVE', 'hash-live', ?, ?) RETURNING id",
            Long::class.java, now, now,
        )!!

        // 스케일 아웃: 같은 DB를 쓰는 인스턴스 C가 새로 뜬다.
        startInstance("instance-c", Files.createTempDirectory("instance-c-uploads").toString()).close()

        val statuses = jdbc.queryForList(
            "SELECT id, status FROM live_broadcast WHERE id IN ($pendingId, $liveId)",
        ).associate { (it["id"] as Number).toLong() to it["status"] }
        assertThat(statuses).isEqualTo(mapOf(pendingId to "PENDING", liveId to "LIVE"))
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
