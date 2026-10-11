import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("org.springframework.boot") version "4.0.8" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.spring") version "2.4.21" apply false
    kotlin("plugin.jpa") version "2.4.21" apply false
    kotlin("kapt") version "2.4.21" apply false
}

allprojects {
    group = "com.example"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    // 실행 모듈(commerce-api)만 자신의 build.gradle.kts 에서 org.springframework.boot 플러그인을 적용한다.
    // 라이브러리 모듈은 Boot BOM 만 가져온다.
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.jetbrains.kotlin.kapt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            // Boot BOM 은 kotlin.version 을 자신이 빌드된 버전으로 고정한다. Boot 플러그인이 없는 라이브러리 모듈에서
            // 컴파일러·stdlib 이 어긋나지 않도록(kapt 가 "GRANULARITY is available only since 2.3.0" 으로 죽는다)
            // 실제 적용된 코틀린 플러그인 버전으로 덮어쓴다.
            mavenBom(SpringBootPlugin.BOM_COORDINATES) {
                bomProperty("kotlin.version", getKotlinPluginVersion())
            }
            // Spring Cloud Config 클라이언트(modu_platform 의 config-service 에서 설정을 받는다). Boot 3.5 ↔ Cloud 2025.0.
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3")
        }
    }

    // JPA 엔티티는 Hibernate 프록시 생성을 위해 open 이어야 한다.
    configure<org.jetbrains.kotlin.allopen.gradle.AllOpenExtension> {
        annotation("jakarta.persistence.Entity")
        annotation("jakarta.persistence.MappedSuperclass")
        annotation("jakarta.persistence.Embeddable")
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // ktlint 는 자신이 번들한 Kotlin 컴파일러(PSI)로 소스를 파싱한다. Boot BOM 이 관리하는 kotlin.version 이 ktlint 클래스패스까지
    // 강제되면 "KtTokens does not have member field HEADER_KEYWORD" 로 깨지므로, ktlint 전용 설정에서는 ktlint 가 요구하는
    // Kotlin 버전을 고정한다. ktlint 버전을 올릴 때 이 값도 함께 맞춘다(ktlint 1.8.0 ↔ Kotlin 2.2.21).
    configurations.matching { it.name.startsWith("ktlint") }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("2.2.21")
            }
        }
    }

    ktlint {
        version.set("1.8.0")
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(false)

        reporters {
            reporter(ReporterType.JSON)
        }

        outputColorName.set("RED")

        filter {
            exclude("**/generated/**")
            exclude("**/Q*.kt")
            include("**/kotlin/**")
        }
    }

    dependencies {
        "implementation"("tools.jackson.module:jackson-module-kotlin")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        // MockMvc 테스트 지원이 Boot 4 에서 별도 스타터로 갈라졌다.
        "testImplementation"("org.springframework.boot:spring-boot-starter-webmvc-test")
        "testImplementation"("com.h2database:h2")
        // Gradle 8+ 에서 JUnit Platform 실행에 필요 (Boot 플러그인 없는 모듈은 자동 추가되지 않음)
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}

tasks.register("cleanAll") {
    dependsOn(subprojects.map { it.tasks.named("clean") })
}

tasks.register("buildAll") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}
