dependencies {
    // 하위 모듈이 RwRepository(JpaRepository) 와 로거를 직접 사용하므로 api 로 노출
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("io.github.microutils:kotlin-logging-jvm:2.0.11")

    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    runtimeOnly("com.mysql:mysql-connector-j")

    // 통계 유스케이스 단위 테스트(GetAdminStatsUseCaseTest)가 리포지토리를 목으로 쓴다 — commerce-api 와 같은 판.
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
}

kapt {
    arguments {
        arg("querydsl.entityAccessors", "true")
        arg("querydsl.addGeneratedAnnotation", "false")
    }
}
