import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("org.springframework.boot") version "3.5.4" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    kotlin("plugin.jpa") version "1.9.25" apply false
    kotlin("kapt") version "1.9.25" apply false
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
            mavenBom(SpringBootPlugin.BOM_COORDINATES)
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

    ktlint {
        version.set("1.3.1")
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
        "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
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
