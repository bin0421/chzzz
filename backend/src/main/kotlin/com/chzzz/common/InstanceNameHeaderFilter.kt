package com.chzzz.common

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.InetAddress

/**
 * 5단계: 응답마다 어느 백엔드 인스턴스가 처리했는지 [HEADER] 헤더로 알린다.
 * 백엔드를 여러 대 띄웠을 때 요청이 나뉘는지, 인스턴스가 바뀌어도 로그인이 이어지는지 확인하는 데 쓴다.
 *
 * 이름은 `instance.name`(환경변수 `INSTANCE_NAME`)이고, 비어 있으면 호스트 이름이다.
 * 컨테이너·Kubernetes 파드의 호스트 이름은 컨테이너 ID·파드 이름이라 따로 정하지 않아도 구분된다.
 * 보안 필터가 끝내는 응답(401·403)에도 실리도록 가장 먼저 실행한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class InstanceNameHeaderFilter(
    @Value("\${instance.name:}") configuredName: String,
) : OncePerRequestFilter() {

    val instanceName: String = configuredName.ifBlank { InetAddress.getLocalHost().hostName }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        response.setHeader(HEADER, instanceName)
        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-Backend-Instance"
    }
}
