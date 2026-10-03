# 커머스 카탈로그 확장(A-1) 설계

날짜: 2026-09-19 · 대상: `backend/commerce-service` · 후속: A-2 어드민 화면, A-3 앱 Compose 재구성, B 거래(장바구니·주문)

## 목표
상품 하나에 이름·설명·가격·사진 1장뿐인 카탈로그를 카테고리, 사진 여러 장, 짧은 소개 + 긴 상세, 정가/판매가, 판매 상태, 옵션 그룹·값·SKU(재고·추가금), 찜까지 갖춘 카탈로그로 넓힌다. 기존 구조(usecase → command/query service → ro/rw 리포지토리, 소프트 삭제, 앱/어드민 두 보안 체인)를 그대로 따른다.

## 결정 사항
- 범위: A(카탈로그) → B(거래). 이 스펙은 A-1 백엔드만.
- 옵션은 조합(SKU)별 재고·추가금. 옵션 없는 상품은 값 없는 SKU 하나.
- 품절은 저장하지 않고 SKU 재고 합 0 으로 계산. 판매 상태는 SELLING/HIDDEN 만 관리자가 정한다.
- 스키마는 JPA `ddl-auto: update`. 새 컬럼은 nullable 또는 기본값 → 기존 행 안전. `category_id` nullable(옛 상품).
- 기존 API 필드 이름은 유지하고 확장만 한다(지금 어드민 화면·테스트가 깨지지 않게).

## 데이터 모델
| 엔티티 / 테이블 | 필드 |
|---|---|
| `Category` / categories | name(50), parent(nullable, 2단계까지), sortOrder, deletedAt |
| `Product` / products | 기존 name·description(소개 500)·price(판매가)·imageUrl(대표, 첫 사진과 동기화)·deletedAt + category(nullable), listPrice(정가, nullable), detail(LONGTEXT, nullable), status(SELLING/HIDDEN, 기본 SELLING), wishCount(기본 0) |
| `ProductImage` / product_images | product, url(500), sortOrder |
| `ProductOptionGroup` / product_option_groups | product, name(30), sortOrder |
| `ProductOptionValue` / product_option_values | group, name(30), sortOrder |
| `ProductSku` / product_skus + sku_option_values | product, optionValues(그룹마다 정확히 하나), extraPrice(기본 0), stock(기본 0) |
| `Wishlist` / wishlists | userId(토큰 sub, 64), product, unique(userId, product) |

Product 는 images/optionGroups/skus 를 `cascade ALL + orphanRemoval` 로 소유한다. 도메인 규칙은 Product 안에 둔다: `replaceImages`, `replaceOptions(groups, skus)`(같은 조합의 SKU 는 id 유지), `isSoldOut()`, `discountRate()`, `totalStock()`.

## API
### 앱 `/api/v1` (aud=modu-commerce)
- `GET /categories` → `[{id, name, children:[{id,name,children:[]}]}]`
- `GET /products?categoryId&q&sort=latest|priceAsc|priceDesc|popular&page=0&size=20` → `{content:[요약], totalElements, totalPages, number, size}`. HIDDEN 제외. categoryId 가 상위면 하위 포함. 요약 = `{id, name, imageUrl, price, listPrice, discountRate, soldOut, wished}`.
- `GET /products/{id}` → 상세 `{요약 필드 + description, detail, status, categoryId, categoryPath:[name], images:[url], optionGroups:[{id,name,values:[{id,name}]}], skus:[{id, optionValueIds:[], extraPrice, stock}], wishCount}`. HIDDEN/삭제는 404.
- `GET /wishlist?page&size` → 요약 페이지(최근 찜 순). `POST /wishlist/{productId}` → 204(멱등). `DELETE /wishlist/{productId}` → 204(멱등). 없는 상품 404.

### 어드민 `/api-admin/v1` (aud=modu-admin + ROLE_ADMIN)
- 카테고리 `GET`(트리, 상품 수 포함) / `POST {name, parentId?, sortOrder?}` / `PUT /{id}` / `DELETE /{id}`(하위 또는 상품이 있으면 400).
- 상품 `GET ?q&categoryId&status&page&size` → 페이지(어드민 요약 = `{id, name, description, imageUrl, price, listPrice, status, totalStock, categoryName}`), `GET /{id}` → 상세(앱 상세 + wished 없음), `POST`, `PUT /{id}`, `DELETE /{id}`.
- 등록/수정 본문: `{name, description, detail?, price, listPrice?, categoryId?, status?, imageUrl?, images?:[url], optionGroups?:[{name, values:[name]}], skus?:[{options:{그룹명:값명}, extraPrice, stock}]}`. `images` 가 없으면 `[imageUrl]`, `skus` 가 없으면 옵션 없는 SKU 하나(재고 0). 응답은 상세.

## 검증 (400 + 첫 메시지)
이름 100자, 소개 500자, 상세 20,000자, 가격 0 이상, 정가는 판매가 이상, 이미지 최대 10장(URL 패턴), 옵션 그룹 최대 3개·그룹당 값 최대 20개·이름 중복 금지, SKU 는 그룹마다 값 하나씩·조합 중복 금지·존재하는 값만, 재고 0 이상, 추가금 0 이상, 카테고리 존재. 정렬 값이 이상하면 latest.

## 시드
카테고리 4개(전자기기·생활·패션·문구)와 하위 10개, 상품 24개(기존 4개 이름 유지). 옵션 예: 티셔츠 색상×사이즈 6 SKU, 이어폰 색상 2, 텀블러 용량 2. 사진 상품마다 2~3장(picsum seed). 상품 테이블이 비어 있을 때만.

## 테스트
- 엔티티: SKU 조합 검증, 품절·할인율·총재고, replaceOptions 의 id 유지.
- 리포지토리: 카테고리 하위 포함·키워드·정렬·HIDDEN 제외 페이징, 찜 페이지.
- 컨트롤러: 앱 목록/상세/찜(권한·페이지·옵션 구조), 어드민 카테고리·상품 등록/수정/삭제·검증 메시지, 옛 본문(images/skus 없음) 호환.
- 시드: 개수, 옵션 상품 존재, 사진 중복 없음.
