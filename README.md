# 모두의 커머스

모두의 채팅과 같은 **모두 계정**으로 로그인하는 커머스입니다. 저장소는 세 부분입니다.

- `web/` — 커머스 화면 전부(React + Vite). Android 앱의 WebView 가 열고, 브라우저에서도 열립니다. 실험 단계라 화면을 빨리 바꿔 보려고 웹으로 둡니다. 자세한 건 `web/README.md`.
- `android/modu_commerce` — Android 껍데기(Kotlin/Compose): 초기 화면 → 로그인(모두 계정 SSO 또는 Google) → **WebView**. 토큰은 `window.ModuApp` 브리지로 웹에 넘기고, 만료되면 refresh 토큰으로 갱신하며, 갱신도 실패하면 로그인 화면으로 돌아갑니다.
- `backend/commerce-service` — 카탈로그·장바구니·주문 API.

## 실행

```bash
# 1) 웹 (Mac, LAN 에 열림)
cd web && npm install && npm run dev            # http://192.168.0.3:5174

# 2) Android 앱 — 디버그 빌드는 위 dev 서버 주소(app/build.gradle.kts 의 WEB_URL)를 연다
cd android/modu_commerce
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- **모두 계정으로 로그인 (채팅 앱)**: 설치된 모두의 채팅 앱에 1회용 코드를 요청하고(PKCE), auth-service `/oauth2/token`(`sso_code` grant)으로 이 앱의 토큰을 받습니다. 채팅 앱과 같은 키(개발: 디버그 키)로 서명돼야 합니다.
- **Google 로 로그인**: 채팅 앱이 없을 때의 폴백. 구글 콘솔에 패키지 `com.example.moducommerce` 와 서명 SHA-1 이 등록돼 있어야 합니다.
- 게이트웨이 주소는 `app/build.gradle.kts` 의 `API_BASE_URL` 입니다.

## 커머스 서비스 (backend/commerce-service)

`~/Downloads/demo` 프로젝트 구조를 따른 Kotlin/Spring Boot 3.5 멀티모듈 서비스입니다.

- `commerce-api`: 실행 모듈.
  - 앱(`aud=modu-commerce`): `GET /api/v1/products`(`?q=` 검색), `GET /api/v1/products/{id}`.
  - 백오피스(`aud=modu-admin` + `roles` 에 `ROLE_ADMIN`): `GET /api-admin/v1/products?q=&page=&size=`, `GET·PUT·DELETE /api-admin/v1/products/{id}`, `POST /api-admin/v1/products`. 게이트웨이의 `/commerce-service/api-admin/**` 로 들어오며, commerce 도 토큰을 다시 검증합니다. 삭제는 `deleted_at` 만 채우는 소프트 삭제입니다.
  - 모든 토큰은 모두의 채팅 auth-service 가 발급한 RS256 토큰을 JWKS 로 검증합니다.
- `commerce-application`: 도메인/저장소/서비스. master(RW)·replica(RO) 데이터소스 분리, RO 는 DDL 을 실행하지 않음, QueryDSL(RO/RW 쿼리 팩토리). 시작 시 `products` 가 비어 있으면 테스트 상품 4개를 넣습니다(사진은 `picsum.photos` 의 seed 주소라 매번 같은 사진이 옵니다). 이미 상품이 들어 있는 DB 는 건드리지 않으므로, 사진을 넣으려면 `products` 를 비우고 다시 띄워야 합니다.

```bash
cd backend/commerce-service
./gradlew test bootJar          # 테스트 37개, commerce-api/build/libs/commerce-api-0.0.1-SNAPSHOT.jar
cd .. && docker compose up -d --build   # commerce-service(8200). mysql-commerce(3316)는 modu_infra(https://github.com/tear94fall/modu_infra, 이 저장소 옆에 clone)의 data 에서 먼저 띄운다
```

접속 정보는 `DB_MASTER_URL`/`DB_MASTER_USERNAME`/`DB_MASTER_PASSWORD`(선택 `DB_REPLICA_*`), 토큰 검증은 `MODU_OAUTH_ISSUER`/`MODU_OAUTH_JWKS_URI` 환경변수로 바꿉니다. 커머스 앱은 로그인 후 `COMMERCE_API_URL`(`app/build.gradle`) 로 상품 목록을 불러옵니다.
