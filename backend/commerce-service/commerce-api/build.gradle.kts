plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":commerce-application"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    // 모두 계정(auth-service) 토큰을 JWKS 로 검증하는 리소스 서버
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    testImplementation("org.springframework.security:spring-security-test")
}

springBoot {
    mainClass.set("com.example.commerce.api.CommerceApiApplicationKt")
}

tasks.jar { enabled = false }
