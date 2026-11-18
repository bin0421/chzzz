package com.chzzz

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

// 모든 통합 테스트가 이 설정을 가져오므로, MockMvc 세션 쿠키 처리도 함께 가져온다.
@TestConfiguration(proxyBeanMethods = false)
@Import(SessionCookieMockMvcConfiguration::class)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	fun postgresContainer(): PostgreSQLContainer {
		return PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
	}

}
