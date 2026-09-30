# 모두의 커머스

모두의 채팅과 같은 **모두 계정**으로 로그인하는 커머스입니다. 저장소는 세 부분입니다.

- `web/` — 커머스 화면 전부(React + Vite). Android 앱의 WebView 가 열고, 브라우저에서도 열립니다. 실험 단계라 화면을 빨리 바꿔 보려고 웹으로 둡니다. 자세한 건 `web/README.md`.
- `android/modu_commerce` — Android 껍데기(Kotlin/Compose): 초기 화면 → 로그인(모두 계정 SSO 또는 Google) → **WebView**. 토큰은 `window.ModuApp` 브리지로 웹에 넘기고, 만료되면 refresh 토큰으로 갱신하며, 갱신도 실패하면 로그인 화면으로 돌아갑니다.
- `backend/commerce-service` — 카탈로그·장바구니·주문 API.

## 모두의 커머스 어플리케이션

| 시작 화면 | 로그인 | 모두 계정으로 로그인 | 홈 |
| :--------: | :--------: | :--------: | :--------: |
|![start](./images/start.jpg)|![login](./images/login.jpg)|![sso_login](./images/sso_login.jpg)|![home](./images/home.jpg)|

| 카테고리 | 카테고리 상품 | 상품 상세 | 상품 리뷰 |
| :--------: | :--------: | :--------: | :--------: |
|![category](./images/category.jpg)|![product_list](./images/product_list.jpg)|![product_detail](./images/product_detail.jpg)|![product_reviews](./images/product_reviews.jpg)|

| 장바구니 | 주문서 | 주문 상세 | 마이페이지 |
| :--------: | :--------: | :--------: | :--------: |
|![cart](./images/cart.jpg)|![checkout](./images/checkout.jpg)|![order_detail](./images/order_detail.jpg)|![my_page](./images/my_page.jpg)|

| 쿠폰함 | 기획전 | 출석 체크 이벤트 | 쿠폰 받기 이벤트 |
| :--------: | :--------: | :--------: | :--------: |
|![coupon_box](./images/coupon_box.jpg)|![exhibition](./images/exhibition.jpg)|![attendance](./images/attendance.jpg)|![coupon_event](./images/coupon_event.jpg)|

---

## 실행

```bash
# 1) 웹 (Mac, LAN 에 열림)
cd web && npm install && npm run dev            # http://192.168.0.3:5174

# 1') 웹 배포본 (nginx, 릴리스 빌드가 여는 주소) — 백엔드 compose 의 서비스라 commerce-service 와 같이 뜬다
cd backend && docker compose up -d --build modu-commerce-web     # http://192.168.0.3:8082

# 2) Android 앱 — 디버그 빌드는 Vite dev 서버(:5174), 릴리스 빌드는 배포본(:8082)을 연다 (app/build.gradle.kts 의 WEB_URL)
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
  - 앱(`aud=modu-commerce`): `/api-public/v1/**` (상품·카테고리·장바구니·주문·리뷰·쿠폰·기획전·포인트·알림 등). 커머스 웹/앱은 같은 출처 `/api-public/` 으로 부르고, nginx·Vite 가 게이트웨이 `/commerce-service/api-public/**` 로 넘깁니다(게이트웨이가 토큰을 확인하고 commerce 도 다시 검증).
  - 백오피스(`aud=modu-admin` + `roles` 에 `ROLE_ADMIN`): `GET /api-admin/v1/products?q=&page=&size=`, `GET·PUT·DELETE /api-admin/v1/products/{id}`, `POST /api-admin/v1/products`. 게이트웨이의 `/commerce-service/api-admin/**` 로 들어오며, commerce 도 토큰을 다시 검증합니다. 삭제는 `deleted_at` 만 채우는 소프트 삭제입니다.
  - 모든 토큰은 모두의 채팅 auth-service 가 발급한 RS256 토큰을 JWKS 로 검증합니다.
- `commerce-application`: 도메인/저장소/서비스. master(RW)·replica(RO) 데이터소스 분리, RO 는 DDL 을 실행하지 않음, QueryDSL(RO/RW 쿼리 팩토리). 시작 시 `products` 가 비어 있으면 샘플 카테고리·상품을 넣습니다. 상품 사진은 상품에 맞는 퍼블릭 도메인 사진(아래 "상품 사진 출처")이고, 목록에 없는 상품만 `picsum.photos` 의 seed 주소를 씁니다. 이미 상품이 들어 있는 DB 는 건드리지 않습니다.

```bash
cd backend/commerce-service
./gradlew test bootJar          # 테스트 37개, commerce-api/build/libs/commerce-api-0.0.1-SNAPSHOT.jar
cd .. && docker compose up -d --build   # commerce-service(8200) + modu-commerce-web(8082). mysql-commerce(3316)는 modu_infra(https://github.com/tear94fall/modu_infra, 이 저장소 옆에 clone)의 data 에서 먼저 띄운다
```

접속 정보는 `DB_MASTER_URL`/`DB_MASTER_USERNAME`/`DB_MASTER_PASSWORD`(선택 `DB_REPLICA_*`), 토큰 검증은 `MODU_OAUTH_ISSUER`/`MODU_OAUTH_JWKS_URI` 환경변수로 바꿉니다. 커머스 앱은 로그인 후 `COMMERCE_API_URL`(`app/build.gradle`) 로 상품 목록을 불러옵니다.

## 주요 도메인 모델

### 커머스 도메인 모델 구조

![domain_model](./images/db/domain_model.png)

- **커머스 고객**은 모두 계정(`user_id`)으로 식별합니다. 회원 정보(이름·이메일·프로필)는 모두의 채팅 member-service 가 갖고, 커머스는 가입(약관 동의)·등급·주문 같은 커머스 쪽 데이터만 둡니다.
- **포인트**는 커머스 DB 에 없습니다. modu_chat 의 point-service(`modu-point` 스키마)가 원장을 갖고, 커머스는 내부 API 로 적립·사용·환불만 요청합니다.
- 다대다 관계(기획전–상품, 기획전–쿠폰, 옵션 값–SKU)는 모두의 채팅과 같은 원칙으로 연관 테이블(`promotion_products`, `promotion_coupons`, `sku_option_values`)을 두어 1대다·1대다로 풀었습니다. `@ManyToMany` 는 쓰지 않습니다.

### 설계 메모

- **외래 키를 거는 곳과 안 거는 곳**: 상품·주문·리뷰·쿠폰처럼 같은 도메인 안의 관계(21개)는 외래 키 제약을 겁니다. 고객(`user_id`), 등급 코드, 쿠폰·상품 id 를 다른 도메인에서 참조하는 곳은 id 만 저장하고 제약을 걸지 않습니다(ERD 의 점선). 무결성은 애플리케이션(유스케이스)이 지킵니다.
- **소프트 삭제**: `products`, `reviews`, `coupons`, `promotions` 는 `deleted_at` 만 채우고 행을 남깁니다(`@SQLRestriction`). 리뷰는 주문 상품당 한 건(`order_item_id` 유니크)입니다.
- **스냅샷 컬럼**: 주문 상품(`product_name`, `option_label`, `unit_price`), 리뷰(`product_name`, `author_name`), 주문(`recipient`, `address1`…)은 주문 시점 값을 복사해 둡니다. 원본이 바뀌어도 주문 내역은 그대로입니다.
- **시각**: 서버는 UTC `datetime(6)` 로 저장하고 앱이 한국 시간으로 바꿉니다. 기획전 기간·쿠폰 유효 기간·등급 산정 기간은 한국 달력 날짜(`date`)입니다.
- **읽기·쓰기 분리**: 쓰기는 `mysql-commerce`(소스), 읽기는 GTID 복제 레플리카 `mysql-commerce-replica` 에 SELECT 전용 계정(`commerce_ro`)으로 붙습니다. 쓰기 직후 다시 읽는 조회(장바구니·배송지·결제 직후 주문·가입 확인 등)는 소스에서 읽습니다.
- **스키마 변경**: 아직 `ddl-auto: update` 로 앱이 테이블을 만듭니다(운영 전환 전에 DBA 주도 변경으로 옮길 예정).

### ERD

실제 dev DB(`commerce` 스키마, MySQL 8.0)에서 뽑은 30개 테이블입니다. 실선은 외래 키, 점선은 id 만 저장하는 논리 관계, `*` 는 NOT NULL 입니다. 도메인별로 나눠 그렸고 다른 도메인의 테이블은 회색 상자로 표시했습니다.

#### 상품·카테고리

![erd_products](./images/db/erd_products.png)

| 테이블 | 내용 |
|---|---|
| `categories` | 카테고리 트리(`parent_id` 자기 참조), 이모지 아이콘·색, 정렬 순서 |
| `products` | 상품. 판매가·정가·상태(SELLING/HIDDEN), 찜 수·리뷰 수·평점 합 캐시, 소프트 삭제(재고는 SKU 에) |
| `product_images` | 상품 사진 URL, 정렬 순서 |
| `product_option_groups` / `product_option_values` | 옵션 그룹(색상 등)과 값 |
| `product_skus` | 옵션 조합별 재고·추가 금액. 장바구니·주문은 SKU 단위 |
| `sku_option_values` | SKU–옵션 값 연관 테이블 |

#### 고객·회원 등급

![erd_customers_tiers](./images/db/erd_customers_tiers.png)

| 테이블 | 내용 |
|---|---|
| `commerce_customers` | 커머스 고객(PK `user_id` = 모두 계정). 가입·약관/개인정보 동의 시각·버전, 상태(ACTIVE/WITHDRAWN), 현재 등급과 산정 기준 금액, 이전 이용자 여부(`migrated`) |
| `commerce_tiers` | 등급 4단계(웰컴·실버·골드·VIP): 기준 금액, 적립률, 색 (어드민에서 수정) |
| `commerce_tier_coupons` | 등급별 매월 자동 발급 쿠폰 |
| `commerce_tier_histories` | 고객별 등급 변경 이력(가입/월 산정/수동, 기준 기간·금액) |
| `commerce_tier_runs` | 산정 실행 기록(월 1일 자동·수동): 고객 수, 변경 수, 등급별 인원, 쿠폰 발급/건너뜀 |

#### 장바구니·배송지·주문·리뷰·찜

![erd_orders](./images/db/erd_orders.png)

| 테이블 | 내용 |
|---|---|
| `cart_items` | 고객별 장바구니(SKU + 수량) |
| `addresses` | 배송지, 기본 배송지 표시 |
| `orders` | 주문. 상태(PAID→SHIPPING→DELIVERED / CANCELLED), 결제 금액·포인트 사용·쿠폰 할인, 배송 완료 시각, 구매 적립 상태(`earn_*`), 배송지 스냅샷 |
| `order_items` | 주문 상품(SKU·수량·단가 스냅샷) |
| `reviews` | 리뷰(주문 상품당 1건, 별점 1~5, 작성자 이름 스냅샷, 숨김/소프트 삭제) |
| `wishlists` | 찜(고객–상품) |

#### 쿠폰

![erd_coupons](./images/db/erd_coupons.png)

| 테이블 | 내용 |
|---|---|
| `coupons` | 쿠폰 정의: 정액/정률·최대 할인·최소 주문 금액, 적용 범위(전체/카테고리/상품), 발급 기간·유효 기간, 수량, 코드, 앱 노출 여부, 소프트 삭제 |
| `coupon_scope_targets` | 적용 범위가 카테고리/상품일 때 대상 id |
| `user_coupons` | 보유 쿠폰: 발급 경로(DOWNLOAD/CODE/ADMIN/EVENT/TIER), 만료일, 사용 주문, 발급 키(`issue_key` — 등급 쿠폰은 `tier:YYYY-MM` 으로 매월 새로 받음) |

#### 기획전·이벤트

![erd_promotions](./images/db/erd_promotions.png)

| 테이블 | 내용 |
|---|---|
| `promotions` | 기획전(EXHIBITION) 또는 이벤트(EVENT: 출석/쿠폰). 기간(한국 날짜), 배너 이미지·색, 노출 여부, 정렬, 출석 보상 포인트 규칙 |
| `promotion_products` / `promotion_coupons` | 기획전에 붙는 상품·쿠폰(연관 테이블, 순서 있음) |
| `attendance_checks` | 출석 체크(고객·이벤트·날짜 유니크), 적립 결과 |

#### 푸시

![erd_push](./images/db/erd_push.png)

| 테이블 | 내용 |
|---|---|
| `push_devices` | 커머스 앱 기기 토큰(FCM, 토큰 유니크, 한 사람이 여러 기기) |
| `push_consents` | 혜택·이벤트 알림 동의, 야간 수신 동의와 각 변경 시각 |
| `push_campaigns` | 관리자 발송 캠페인: 내용·이미지·이동할 곳, 예약 시각, 상태(SCHEDULED/SENDING/SENT/CANCELED/FAILED), 발송·성공·실패·열어 봄 수 |
| `push_campaign_opens` | 캠페인을 눌러 들어온 기록(고객당 1회) |
| `push_inbox_items` | 앱 알림함(실제로 받은 사람마다 1건, 읽음 시각) |

## 상품 사진 출처

샘플 상품 사진은 [Openverse](https://openverse.org) 에서 고른 CC0 · 퍼블릭 도메인(PDM) 사진입니다. 출처 표시 의무는 없지만 원본을 남겨 둡니다. 앱은 원본 CDN 주소를 그대로 씁니다(`ProductSeeder.SAMPLE_IMAGES`).

| 상품 | 출처 | 라이선스 | 원본 |
| --- | --- | --- | --- |
| 모두 무선 이어폰 | flickr | PDM | [superbsavers](https://www.flickr.com/photos/195657162@N08/52063601444) |
| 모두 무선 이어폰 | flickr | PDM | [maniban01](https://www.flickr.com/photos/189514139@N06/50168607566) |
| 모두 블루투스 스피커 | rawpixel | CC0 | [원본](https://www.rawpixel.com/image/5975218/closeup-black-bluetooth-speaker) |
| 모두 블루투스 스피커 | rawpixel | CC0 | [원본](https://www.rawpixel.com/image/5915300/image-background-public-domain-technology) |
| 모두 기계식 키보드 | stocksnap | CC0 | [Bridget Braun](https://stocksnap.io/photo/iphone-keyboard-R7GVMRJWW9) |
| 모두 기계식 키보드 | stocksnap | CC0 | [Jaroslaw%20Puszczy%u0144ski](https://stocksnap.io/photo/keyboard-computer-FFPJ3S8U5Y) |
| 모두 무선 마우스 | wordpress | CC0 | [Ajith R N](https://wordpress.org/photos/photo/8756533cf9/) |
| 모두 무선 마우스 | rawpixel | CC0 | [원본](https://www.rawpixel.com/image/6020645/photo-image-public-domain-hand-person) |
| 모두 고속 충전기 65W | rawpixel | CC0 | [원본](https://www.rawpixel.com/image/5923136/photo-image-phone-public-domain-white) |
| 모두 고속 충전기 65W | rawpixel | CC0 | [원본](https://www.rawpixel.com/image/5910187/photo-image-phone-public-domain-technology) |
| 모두 보조배터리 10000 | stocksnap | CC0 | [Freestocks.org](https://stocksnap.io/photo/iphone-mobile-1KQALMC305) |
| 모두 보조배터리 10000 | flickr | CC0 | [cogdogblog](https://www.flickr.com/photos/37996646802@N01/31300186927) |
| 모두 텀블러 500ml | stocksnap | CC0 | [Nathan Dumlao](https://stocksnap.io/photo/thermos-heater-Q9JPW18WWZ) |
| 모두 텀블러 500ml | stocksnap | CC0 | [Simon Migaj](https://stocksnap.io/photo/adventure-coffee-IB5PS7EDFG) |
| 모두 머그컵 세트 | stocksnap | CC0 | [Clem Onojeghuo](https://stocksnap.io/photo/coffee-mug-J6PXDIMIUU) |
| 모두 머그컵 세트 | flickr | PDM | [favorli](https://www.flickr.com/photos/147778363@N07/32903357261) |
| 모두 호텔 수건 4장 | rawpixel | CC0 | [원본](https://www.rawpixel.com/image/5922052/bath-towels-free-public-domain-cc0-photo) |
| 모두 호텔 수건 4장 | rawpixel | CC0 | [원본](https://www.rawpixel.com/image/5921967/bath-towels-free-public-domain-cc0-photo) |
| 모두 샤워 타월 | flickr | PDM | [hongking1](https://www.flickr.com/photos/197668716@N04/52676443903) |
| 모두 샤워 타월 | rawpixel | CC0 | [원본](https://www.rawpixel.com/image/6017422/bath-towel-free-public-domain-cc0-photo) |
| 모두 무드등 | stocksnap | CC0 | [Atilla Taskiran](https://stocksnap.io/photo/still-items-8ZYCJ5MGJI) |
| 모두 무드등 | stocksnap | CC0 | [Frank Oschatz](https://stocksnap.io/photo/office-desk-WDUTVSMPXQ) |
| 모두 디퓨저 | wikimedia | CC0 | [Alex Tang](https://commons.wikimedia.org/w/index.php?curid=56224537) |
| 모두 디퓨저 | flickr | CC0 | [Sulen Lee](https://www.flickr.com/photos/133369866@N05/17864691244) |
| 모두 데일리 백팩 | stocksnap | CC0 | [Cynthia del Rio](https://stocksnap.io/photo/white-bed-0JXUC43X55) |
| 모두 데일리 백팩 | stocksnap | CC0 | [Felix Russell-Saw](https://stocksnap.io/photo/yellow-backpack-LE23HHZVIF) |
| 모두 크로스백 | flickr | PDM | [niceleather](https://www.flickr.com/photos/130665648@N08/17281999865) |
| 모두 크로스백 | flickr | CC0 | [shop8447](https://www.flickr.com/photos/185514373@N06/49062061267) |
| 모두 베이직 티셔츠 | rawpixel | CC0 | [원본](https://www.rawpixel.com/image/9746837/image-people-pattern-illustrations) |
| 모두 베이직 티셔츠 | flickr | CC0 | [sarahstierch](https://www.flickr.com/photos/7633518@N08/54573341777) |
| 모두 후드 집업 | wikimedia | CC0 | [T Cells](https://commons.wikimedia.org/w/index.php?curid=102904540) |
| 모두 후드 집업 | flickr | CC0 | [Wonderlane](https://www.flickr.com/photos/71401718@N00/12358351054) |
| 모두 볼캡 | stocksnap | CC0 | [Lautaro Andreani](https://stocksnap.io/photo/cap-hat-RI84WJYDVA) |
| 모두 볼캡 | stocksnap | CC0 | [Studio 7042](https://stocksnap.io/photo/small-boy-QIJ4BAZL2D) |
| 모두 버킷햇 | stocksnap | CC0 | [Alex%20Bl%u0103jan](https://stocksnap.io/photo/straw-hat-R27ZN6PJ4S) |
| 모두 버킷햇 | flickr | CC0 | [Wonderlane](https://www.flickr.com/photos/71401718@N00/51694681673) |
| 모두 하드커버 노트 | stocksnap | CC0 | [Kristin Hardwick](https://stocksnap.io/photo/journal-desk-MFRLKOXJVH) |
| 모두 하드커버 노트 | stocksnap | CC0 | [Ian Schneider](https://stocksnap.io/photo/office-work-CH9EXU7YTW) |
| 모두 젤펜 5색 | stocksnap | CC0 | [Jeffrey Betts](https://stocksnap.io/photo/pens-pencils-7UES4TX4ZN) |
| 모두 젤펜 5색 | stocksnap | CC0 | [Tim Gouw](https://stocksnap.io/photo/pens-pencils-0V4MVUGT47) |
| 모두 데스크 매트 | stocksnap | CC0 | [Jeff Sheldon](https://stocksnap.io/photo/apple-mac-51CE04FEF9) |
| 모두 데스크 매트 | stocksnap | CC0 | [Andrew Pons](https://stocksnap.io/photo/mac-desktop-UCEBZORVVB) |
| 모두 모니터 받침대 | stocksnap | CC0 | [Serpstat](https://stocksnap.io/photo/seo-computer-959IURDRGJ) |
| 모두 모니터 받침대 | stocksnap | CC0 | [Volkan Olmez](https://stocksnap.io/photo/apple-mac-0061559E5D) |
| 모두 다이어리 2027 | stocksnap | CC0 | [Kristin Hardwick](https://stocksnap.io/photo/writing-hand-2FRRD2PUVA) |
| 모두 다이어리 2027 | stocksnap | CC0 | [Kristin Hardwick](https://stocksnap.io/photo/writing-hand-JTHDMAOGLD) |
| 모두 스티커 팩 | wikimedia | CC0 | [Reconrabbit](https://commons.wikimedia.org/w/index.php?curid=157271527) |
| 모두 스티커 팩 | stocksnap | CC0 | [Dan Gold](https://stocksnap.io/photo/stickers-kitchen-UD4G1NRANC) |
