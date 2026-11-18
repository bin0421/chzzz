---
Status: accepted (5단계)
---

# 로그인 세션을 PostgreSQL에 두어 백엔드 인스턴스끼리 공유한다

ADR-0005의 "세션은 백엔드 메모리" 부분을 대체한다. 5단계에서 백엔드가 2~5대로 늘고 줄면 요청마다 다른 인스턴스가 받는다. 세션이 메모리에 있으면 로그인한 인스턴스가 아닌 곳에서는 로그아웃된 것처럼 보이고, 인스턴스가 줄면 그 인스턴스의 로그인이 모두 사라진다.

Spring Session JDBC(`spring-boot-starter-session-jdbc`)로 세션을 이미 쓰고 있는 PostgreSQL에 둔다. 서블릿 세션 API를 그대로 감싸므로 인증 코드(로그인 시 세션 ID 교체, 로그아웃 무효화, 401/403 응답)는 바꾸지 않는다. CSRF 토큰은 원래 쿠키(`XSRF-TOKEN`)에 있어서 인스턴스끼리 나눌 것이 없다.

- 세션 테이블(`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`)은 Flyway `V5`로 만든다. Spring Session의 자동 스키마 생성(`spring.session.jdbc.initialize-schema`)은 끈다. 스키마는 Flyway 한 곳에서만 바뀌어야 하기 때문이다.
- 세션 쿠키 이름은 `JSESSIONID`로 유지한다(Spring Session 기본값은 `SESSION`). `HttpOnly`도 그대로다.
- 만료 세션 정리는 Spring Session 기본 정리 작업(매분)에 맡긴다. 인스턴스마다 돌지만 만료된 행을 지우는 `DELETE`라 겹쳐도 결과가 같다.

## Considered Options

- **스티키 세션(로드밸런서가 같은 인스턴스로 보냄)**: 코드가 그대로지만 인스턴스가 줄거나 재시작하면 로그인이 사라지고, 부하가 고르게 나뉘지 않는다. kube-proxy(Service)만으로는 쿠키 기반 고정이 안 되어 인그레스가 필요하다.
- **Spring Session + Redis**: 빠르고 만료 처리가 자연스럽지만 운영할 저장소가 하나 늘어난다. 과제 규모에서는 이미 있는 PostgreSQL로 충분하다.
- **JWT로 무상태 인증**: 서버가 세션을 갖지 않지만 3단계 인증 구조(ADR-0005)를 다시 짜야 하고, 로그아웃 즉시 무효화가 어렵다.

## Consequences

- 백엔드를 재시작해도 로그인이 남는다. compose 경로도 같은 코드라 마찬가지다(ADR-0005의 "재시작하면 모두 로그아웃"이 바뀐다).
- 요청마다 세션 조회·갱신 쿼리가 DB로 간다. 부하가 커지면 DB가 먼저 병목이 될 수 있다(5단계 범위에서는 받아들인다).
- 세션 속성은 자바 직렬화로 저장한다. 세션에 담는 로그인 회원(`LoginMember`)은 `Serializable`이고, 이 클래스를 바꾸면 이미 저장된 세션을 못 읽어 다시 로그인해야 할 수 있다.
- Spring Session의 쿠키는 `SameSite=Lax`가 붙는다(이전 Tomcat 쿠키에는 없었고 브라우저 기본값이 `Lax`라 동작은 같다). 결제 후 토스에서 돌아오는 화면 이동은 최상위 GET이라 쿠키가 실린다.
- MockMvc 테스트는 서블릿 컨테이너 세션 대신 세션 쿠키로 로그인 상태를 이어 가야 해서, 테스트 설정이 응답의 세션 쿠키를 기억해 싣는다.
