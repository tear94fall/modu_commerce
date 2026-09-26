plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":commerce-application"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    // admin 상품 등록·수정 요청 검증(@Valid)
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // 모두 계정(auth-service) 토큰을 JWKS 로 검증하는 리소스 서버
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    // 기획전·이벤트 캐시. 개발·운영은 공용 Redis 클러스터(modu_infra), 테스트·로컬은 메모리 캐시
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    testImplementation("org.springframework.security:spring-security-test")
    // 코틀린 non-null 파라미터에 Mockito 매처(eq/any)를 쓰기 위해
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

springBoot {
    mainClass.set("com.example.commerce.api.CommerceApiApplicationKt")
}

tasks.jar { enabled = false }
