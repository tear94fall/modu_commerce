# 상품 CRUD · 앱 상세/검색 · admin 상품 관리 설계

- 날짜: 2026-09-14
- 범위: commerce-service(백엔드), 모두의 커머스 안드로이드 앱, 모두메신저 게이트웨이·백오피스(admin)
- 브랜치: `modu_commerce` → `feature/product-crud` (develop + feature/login-then-products 머지), `modu_messenger` → `feature/commerce-products-admin`

## 목표

1. commerce-service 에 상품 등록·수정·삭제 API 를 추가한다. 쓰기는 관리자만 할 수 있다.
2. 앱에서 상품을 검색하고, 카드를 눌러 상세를 볼 수 있다.
3. admin 에서 상품 목록을 보고 등록·수정·삭제할 수 있다.

## 결정 사항

| 항목 | 결정 |
|---|---|
| admin → commerce 경로 | 게이트웨이 라우트 추가 + commerce 가 JWT 를 직접 재검증 (내부 토큰 안 씀) |
| 삭제 | 소프트 삭제 (`deleted_at`) |
| 상품 사진 | 이미지 URL 직접 입력 (업로드 없음) |
| 앱 조회 범위 | 상세 화면 + 검색 |
| admin 범위 | 목록 · 등록 · 수정 · 삭제 |

## ① commerce 백엔드

### API

| 호출자 | 메서드 · 경로 | 동작 |
|---|---|---|
| 앱 (`aud=modu-commerce`) | `GET /api/v1/products?q=` | 기존. 삭제된 상품 제외 |
| 앱 | `GET /api/v1/products/{id}` | 기존. 삭제된 상품은 404 |
| admin (`aud=modu-admin` + `ROLE_ADMIN`) | `GET /api-admin/v1/products?q=&page=&size=` | 페이지 목록, id 내림차순(최신 등록순). 응답은 `{content,totalElements,totalPages,number,size}` |
| admin | `GET /api-admin/v1/products/{id}` | 단건. 없으면 404 |
| admin | `POST /api-admin/v1/products` | 등록. 201 + 상품 |
| admin | `PUT /api-admin/v1/products/{id}` | 전체 필드 교체. 200 + 상품. 없으면 404 |
| admin | `DELETE /api-admin/v1/products/{id}` | 소프트 삭제. 204. 없거나 이미 삭제면 404 |

- `page` 는 0 이상으로, `size` 는 1~100 으로 자른다. 기본값은 `page=0`, `size=15`(admin `PAGE_SIZE` 와 같음).
- 요청 본문: `{ name, description, price, imageUrl }`.

### 입력 검증 (`spring-boot-starter-validation`)

| 필드 | 규칙 |
|---|---|
| name | 필수, 공백만은 불가, 100자 이하 |
| description | 500자 이하. 없거나 null 이면 빈 문자열로 저장 |
| price | 필수, 0 이상 |
| imageUrl | 선택, 500자 이하, `http://` 또는 `https://` 로 시작. 빈 문자열은 null 로 저장 |

실패하면 400 + `ErrorResponse{message}`. message 는 첫 번째 위반 필드와 이유를 담는다(예: `name: 상품 이름을 입력해 주세요.`). 본문이 JSON 이 아니면 400.

### 인증

`SecurityFilterChain` 을 둘로 나눈다.

1. `securityMatcher("/api-admin/**")`, `@Order(1)`: 전용 `JwtDecoder` 가 iss 와 `aud=modu-admin` 을 검증한다. `roles` 클레임을 권한으로 바꾸고(접두사 없이 그대로) `hasAuthority("ROLE_ADMIN")` 을 요구한다. 토큰 없음·서명/aud 불일치 → 401, 역할 없음 → 403.
2. 나머지: 기존과 같이 `aud=modu-commerce`.

설정: `modu.oauth.admin-audience` (기본 `modu-admin`). 게이트웨이도 같은 검사를 하지만 8200 포트로 직접 오는 요청 때문에 commerce 가 다시 확인한다.

### 도메인 · 저장소

- `Product` 에 `deleted_at`(nullable) 컬럼, `update(name, description, price, imageUrl)`, `delete()` 추가.
- 엔티티에 `@SQLRestriction("deleted_at IS NULL")` 을 붙여 모든 조회에서 삭제된 행을 뺀다.
- 컬럼은 master 의 `ddl-auto: update` 가 추가한다. RO 는 DDL 을 실행하지 않는다(기존 규칙).
- 시더는 삭제된 행까지 센다(`ProductRwRepository.countIncludingDeleted()`, 네이티브 쿼리). 관리자가 상품을 전부 지워도 재기동 때 테스트 상품이 되살아나지 않는다.
- 쓰기: `ProductCommandService` (`rwTransactionManager`, `ProductRwRepository`). 등록·수정 응답은 저장한 RW 엔티티로 만든다(복제 지연 회피). 수정·삭제 대상도 RW 에서 찾는다.
- 읽기: `ProductCustomRepository.searchPage(keyword, pageable)` (QueryDSL, RO) 를 추가한다.
- 유스케이스: `CreateProductUseCase`, `UpdateProductUseCase`, `DeleteProductUseCase`, `SearchAdminProductsUseCase`. 명령 입력은 `usecase/command/ProductCommand`.

### 테스트

- application: 등록/수정/삭제, 삭제 후 앱 조회·검색·단건·admin 페이지에서 사라짐, 페이지 검색(정렬·총 개수), 없는 상품 수정·삭제 시 `EntityNotFoundException`.
- api: admin 엔드포인트 401(토큰 없음) / 403(ROLE_ADMIN 없음) / 400(검증) / 201→200→204→404 흐름. admin 토큰은 앱 API 에서 쓸 수 없음은 aud 검증기 단위 테스트로 확인.
- 기존 18개 테스트는 계속 통과한다.

## ② 게이트웨이 · admin

### 게이트웨이 (`modu_messenger/backend/gateway-service/src/main/resources/application.yml`)

admin 계층에 추가:

```yaml
- id: commerce-service-admin
  uri: ${COMMERCE_SERVICE_URI:http://commerce-service:8200}
  predicates:
    - Path=/commerce-service/api-admin/**
  filters:
    - AuthorizationHeaderFilter=ROLE_ADMIN,modu-admin
    - RewritePath=/commerce-service/(?<segment>.*), /$\{segment}
```

- commerce-service 는 Eureka 에 없으므로 `modu-infra` 네트워크의 컨테이너 이름으로 간다.
- `X-Internal-Token` 은 붙이지 않는다. `Authorization` 헤더는 그대로 전달된다.
- CORS 는 기존 설정(GET/POST/PUT/DELETE)으로 충분하다.
- 반영하려면 gateway 컨테이너를 다시 빌드·기동한다.

### admin (`modu_messenger/admin`)

- `src/api/products.ts`: `searchProducts(keyword, page)`, `getProduct(id)`, `createProduct(input)`, `updateProduct(id, input)`, `deleteProduct(id)`. 경로 `/commerce-service/api-admin/v1/products`. 기존 `api()` 와 `Page<T>` 를 쓴다.
- `util/format.ts` 에 `formatPrice` (`89000` → `89,000원`).
- 사이드바 "채팅방" 아래에 **상품** 메뉴.
- `/products` `ProductsPage`: 검색 폼, "상품 등록" 버튼, 표(번호 / 사진 / 이름 / 가격 / 설명), `Pager`. 행 클릭 → `/products/:id`. 검색하면 0 페이지로.
- `/products/new`, `/products/:id` `ProductFormPage` (한 컴포넌트):
  - 필드: 이름, 가격(number), 설명(textarea), 이미지 URL + 미리보기(`<img>`, 실패 시 "이미지를 불러올 수 없습니다").
  - 등록: 성공하면 `/products` 로 이동.
  - 수정: 불러온 값으로 채우고 "저장" → "저장했습니다". "삭제"(`btn--danger`) → `window.confirm("'<이름>' 상품을 삭제할까요?")` → 확인 시 삭제 후 `/products`.
  - 오류: 400 → 서버 message, 404 → "상품을 찾을 수 없습니다" + 목록 링크, 그 밖 → "저장하지 못했습니다"/"삭제하지 못했습니다"/"상품을 불러오지 못했습니다".
  - 전송 형태: price 는 숫자, 빈 이미지 URL 은 `null`.
- 테스트(vitest + Testing Library, API 모듈 `vi.spyOn`): 목록 렌더·검색 시 0페이지·행 클릭 이동, 등록 전송 형태, 수정 불러오기·저장, 삭제 확인/취소, 400·404 표시, `products.ts` 메서드·경로.
- README 기능 목록에 상품 관리 추가.

## ③ 안드로이드 앱

### 검색 (`ProductListActivity`)

- 앱바 `SearchView`(메뉴 항목, 항상 표시). 로그아웃은 오버플로 유지.
- 검색 버튼(submit) 때만 `GET /api/v1/products?q=` 호출. 닫으면 전체 목록.
- 빈 결과: 검색어 있으면 `'<검색어>' 검색 결과가 없습니다`, 없으면 `보여 줄 상품이 없습니다`.
- 상세에서 돌아오면(`onRestart`) 현재 검색어로 다시 불러온다.

### 상세 (`ProductDetailActivity`, `activity_product_detail.xml`)

- 목록 카드 클릭 → id 만 넘겨 연다. `GET /api/v1/products/{id}` 로 최신 값을 받는다.
- 스크롤 안에 전체 폭 정사각형 사진(Glide, 없으면 로고 플레이스홀더) → 이름 22sp bold → 가격 20sp brand_red bold → 구분선 → 설명 15sp. 앱바 뒤로가기.
- 상태: 불러오는 중 / 404 `삭제됐거나 없는 상품입니다` / 그 밖 `상품을 불러오지 못했습니다`.
- `ProductAdapter` 에 클릭 콜백, 카드 ripple.

### 토큰 갱신 헬퍼

- `TokenRefresher.withFreshAccess { access -> ... }`: 호출이 `AuthException(401)` 이면 refresh 토큰으로 한 번 갱신·저장 후 재시도. refresh 토큰이 없거나 갱신 호출이 실패하면 `AuthException(401)` 로, 재시도가 다시 401 이면 그 예외를 그대로 던진다(반복 없음). 401 이 아닌 오류는 갱신하지 않는다.
- 화면은 이 예외가 401 이면 토큰을 비우고 로그인 화면으로 보낸다(세션 만료 문구). `/userinfo`, 목록, 상세가 모두 이 헬퍼를 쓴다.
- JVM 테스트를 위해 저장소·갱신 함수는 생성자로 주입한다.

### API 클라이언트 (`CommerceApiClient`)

- `products(access, query)`: 검색어가 null/공백이면 `q` 를 붙이지 않는다. 값은 URL 인코딩.
- `product(access, id)` 추가. `Product.parse(json)` 단건 파싱.

### 테스트 (JVM)

- 목록 URL: 검색어 없음/공백 → `q` 없음, 한글 → 인코딩.
- 단건 JSON 파싱.
- `withFreshAccess`: 성공 시 갱신 안 함 / 401 → 갱신 → 재시도 1회 / refresh 없음 → 401 / 401 아닌 오류 → 갱신 안 함 / 재시도도 401 → 반복 안 함 / 갱신 실패 → 401 / 저장된 토큰 없음 → 401.
- 빈 목록 문구: 검색어 있음/없음.

## 배포 · 검증

- 커밋만 하고 푸시하지 않는다.
- commerce-service 재빌드·재기동(`deleted_at` 컬럼 추가), gateway 재빌드·재기동, admin `npm run dev`.
- 플립 3(SM-F711N)에 앱을 설치해 admin 에서 등록·수정·삭제한 상품이 앱 목록·검색·상세에 반영되는지 확인한다.

## 범위 밖

- 이미지 파일 업로드, 재고·카테고리·주문, 삭제 복구 UI, 앱의 당겨서 새로고침.
