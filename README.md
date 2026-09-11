# 모두의 커머스 (SSO 샘플)

모두의 채팅과 같은 **모두 계정**으로 로그인하는 최소 안드로이드 앱입니다(Kotlin). 아이콘은 채팅 앱과 같은 번개 마크에 붉은 그라데이션을 입혔습니다.

- **모두 계정으로 로그인 (채팅 앱)**: 설치된 모두의 채팅 앱에 1회용 코드를 요청하고(PKCE), auth-service `/oauth2/token`(`sso_code` grant)으로 이 앱의 토큰을 받습니다. 채팅 앱이 로그인돼 있으면 확인 대화상자 한 번으로 끝납니다.
- **Google 로 로그인**: 채팅 앱이 없을 때의 폴백. 구글 ID 토큰을 `google_id_token` grant 로 교환합니다. 구글 콘솔에 이 앱의 패키지 `com.example.moducommerce` 와 서명 SHA-1 이 Android 클라이언트로 등록돼 있어야 동작합니다.

## 실행

```bash
cd android/modu_commerce
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

안드로이드 프로젝트는 `android/modu_commerce` 에 있고(Kotlin 소스는 `app/src/main/kotlin`), 서버 주소는 그 안의 `app/build.gradle` 의 `API_BASE_URL` 입니다. 채팅 앱과 같은 키(개발: 디버그 키)로 서명돼야 채팅 앱의 SSO 액티비티를 부를 수 있습니다.

## 커머스 서비스 (backend/commerce-service)

`~/Downloads/demo` 프로젝트 구조를 따른 Kotlin/Spring Boot 3.5 멀티모듈 서비스입니다.

- `commerce-api`: 실행 모듈. `GET /api/v1/products`(`?q=` 검색), `GET /api/v1/products/{id}`. 모든 API 는 모두의 채팅 auth-service 가 발급한 RS256 토큰을 JWKS 로 검증하고 `aud=modu-commerce` 만 허용합니다.
- `commerce-application`: 도메인/저장소/서비스. master(RW)·replica(RO) 데이터소스 분리, RO 는 DDL 을 실행하지 않음, QueryDSL(RO/RW 쿼리 팩토리). 시작 시 `products` 가 비어 있으면 테스트 상품 4개를 넣습니다.

```bash
cd backend/commerce-service
./gradlew test bootJar          # 테스트 15개, commerce-api/build/libs/commerce-api-0.0.1-SNAPSHOT.jar
cd .. && docker compose up -d --build   # mysql-commerce(3316) + commerce-service(8200)
```

접속 정보는 `DB_MASTER_URL`/`DB_MASTER_USERNAME`/`DB_MASTER_PASSWORD`(선택 `DB_REPLICA_*`), 토큰 검증은 `MODU_OAUTH_ISSUER`/`MODU_OAUTH_JWKS_URI` 환경변수로 바꿉니다. 커머스 앱은 로그인 후 `COMMERCE_API_URL`(`app/build.gradle`) 로 상품 목록을 불러옵니다.
