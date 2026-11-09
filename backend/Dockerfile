# 1) 빌드: 이미지 안에서 Gradle wrapper로 실행 가능한 jar를 만든다 (호스트에 JDK 불필요)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 소스와 분리해서 소스만 바뀌면 의존성 다운로드를 다시 하지 않는다
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
RUN ./gradlew bootJar --no-daemon -x test \
    && cp build/libs/*.jar app.jar

# 2) 실행: JRE만 담은 이미지에서 jar를 실행한다
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /app/data/uploads && chown -R app:app /app/data
COPY --from=build /workspace/app.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
