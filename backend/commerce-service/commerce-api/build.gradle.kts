plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":commerce-application"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    // 컨테이너 준비·생존 검사(/actuator/health/readiness, /liveness). 서비스 포트(도커 네트워크 안)에서만 열린다.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // /actuator/prometheus. Prometheus 가 서비스 포트에서 긁어 간다(SecurityConfig 가 토큰 없이 연다).
    implementation("io.micrometer:micrometer-registry-prometheus")
    // 로그를 한 줄 JSON 으로 stdout 에 쓴다(logback-spring.xml). OTel Collector 가 모아 OpenSearch 로 보낸다.
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    // admin 상품 등록·수정 요청 검증(@Valid)
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // API 문서(GET /v3/api-docs, JSON 만). 화면은 시스템 콘솔(modu-system)이 게이트웨이를 거쳐 그린다. Boot 3.5 ↔ springdoc 2.8.x
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.17")
    // 모두 계정(auth-service) 토큰을 JWKS 로 검증하는 리소스 서버
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    // 기획전·이벤트 캐시. 개발·운영은 공용 Redis 클러스터(modu_infra), 테스트·로컬은 메모리 캐시
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // 푸시 캠페인. 커머스 앱(com.example.moducommerce)으로 FCM 을 직접 보낸다(서비스 계정 키는 backend/secrets, 커밋 안 함)
    // google-cloud-storage 가 끌고 오는 jackson-dataformat-xml 은 뺀다. 있으면 Spring 이 XML 컨버터를 등록해
    // RestClient(포인트·회원 호출) 본문이 XML 로 나간다. Storage 는 쓰지 않는다.
    implementation("com.google.firebase:firebase-admin:9.11.0") {
        exclude(group = "com.fasterxml.jackson.dataformat", module = "jackson-dataformat-xml")
    }
    // 스케줄러 분산 잠금. 파드가 2개 이상이어도 등급 산정·적립 재시도·푸시 캠페인이 한 곳에서만 돈다(shedlock 테이블, SchedulerLockConfig).
    implementation("net.javacrumbs.shedlock:shedlock-spring:6.3.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:6.3.0")
    // point-service 호출의 회로 차단기(PointClient). 레지스트리 빈과 Micrometer 지표(resilience4j_circuitbreaker_*)를 자동 구성한다.
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    testImplementation("org.springframework.security:spring-security-test")
    // 코틀린 non-null 파라미터에 Mockito 매처(eq/any)를 쓰기 위해
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

springBoot {
    mainClass.set("com.example.commerce.api.CommerceApiApplicationKt")
}

tasks.jar { enabled = false }
