import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 푸시(FCM)용 Firebase 설정. app/google-services.json 은 gitignore 되어 있고, 없으면 플러그인을 걸지 않는다.
// 그래도 빌드는 되고, 앱은 Firebase 가 초기화되지 않았음을 보고 푸시 기능만 끈다(PushTokens).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.example.moducommerce"
    compileSdk = 35

    defaultConfig {
        // 기존 XML 앱과 같은 applicationId·서명. 채팅 앱의 SSO 허용 목록이 이 패키지를 본다.
        applicationId = "com.example.moducommerce"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"

        // 모두의 채팅과 같은 개발 게이트웨이. auth-service 는 게이트웨이 경유로 부른다.
        buildConfigField("String", "API_BASE_URL", "\"http://192.168.0.3:8000/\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        // 커머스 화면(web/) 주소. 디버그는 Mac 의 Vite dev 서버(LAN, HMR — `cd web && npm run dev -- --host`)를 열어 저장 즉시 반영되고,
        // 릴리스는 dev k8s 의 nginx 정적 빌드(modu_infra k8s, :8082)를 연다. 평소 폰에는 릴리스를 깐다(dev 서버 없이도 돈다).
        debug {
            buildConfigField("String", "WEB_URL", "\"http://192.168.0.3:5174/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "WEB_URL", "\"http://192.168.0.3:8082/\"")
            // 배포용 키가 없어 디버그 키로 서명한다(개인 dev). 스토어 배포 때는 signingConfigs 를 따로 둔다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.android.material)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.play.services.auth)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
