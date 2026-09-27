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
    // 푸시 캠페인. 커머스 앱(com.example.moducommerce)으로 FCM 을 직접 보낸다(서비스 계정 키는 backend/secrets, 커밋 안 함)
    // google-cloud-storage 가 끌고 오는 jackson-dataformat-xml 은 뺀다. 있으면 Spring 이 XML 컨버터를 등록해
    // RestClient(포인트·회원 호출) 본문이 XML 로 나간다. Storage 는 쓰지 않는다.
    implementation("com.google.firebase:firebase-admin:9.11.0") {
        exclude(group = "com.fasterxml.jackson.dataformat", module = "jackson-dataformat-xml")
    }

    testImplementation("org.springframework.security:spring-security-test")
    // 코틀린 non-null 파라미터에 Mockito 매처(eq/any)를 쓰기 위해
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

springBoot {
    mainClass.set("com.example.commerce.api.CommerceApiApplicationKt")
}

tasks.jar { enabled = false }
