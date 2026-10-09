# syntax=docker/dockerfile:1.7
# 1단계: 소스에서 jar 를 만든다. 어디서 빌드해도 같은 이미지(CI·로컬 공통). Gradle 캐시는 BuildKit 캐시 마운트로 재사용한다.
FROM amazoncorretto:21 AS build
# amazoncorretto 에는 xargs 가 없어 gradlew 가 바로 죽는다("xargs is not available"). findutils 만 넣는다.
RUN dnf -y install findutils && dnf clean all
WORKDIR /src
# 의존성 레이어: 빌드 스크립트만 먼저 복사해 소스가 바뀌어도 의존성 다운로드는 캐시된다
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY commerce-api/build.gradle.kts commerce-api/
COPY commerce-application/build.gradle.kts commerce-application/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies -q >/dev/null 2>&1 || true
COPY . .
# 테스트·ktlint 는 여기서 돌리지 않는다(워크플로 images.yml 이 돌린다). -x test 로 로컬 --build 를 빠르게.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -x test :commerce-api:bootJar \
 && cp commerce-api/build/libs/commerce-api-*-SNAPSHOT.jar /app.jar

# 2단계: 실행. exec 형식이라 java 가 PID 1 로 SIGTERM 을 받는다(server.shutdown=graceful). 힙은 컨테이너 메모리 제한의 75%.
# JVM 시간대는 UTC 로 못박는다. 앱의 LocalDateTime 은 UTC 이고 JDBC(serverTimezone=Asia/Seoul)가 DB 에 KST 벽시계로 바꿔 쓴다 —
# 기존 행이 모두 그 짝으로 쓰였으므로 JVM 시간대(TZ)가 바뀌면 새 행만 9시간 어긋난다.
FROM amazoncorretto:21
COPY --from=build /app.jar /commerce-service.jar
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-Duser.timezone=UTC","-jar","/commerce-service.jar"]
