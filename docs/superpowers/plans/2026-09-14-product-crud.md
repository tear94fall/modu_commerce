# 상품 CRUD · 앱 상세/검색 · admin 상품 관리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** commerce-service 에 관리자 전용 상품 등록·수정·소프트 삭제 API 를 만들고, admin 에 상품 관리 페이지를, 앱에 검색·상세 화면을 추가한다.

**Architecture:** admin 은 게이트웨이 `/commerce-service/api-admin/**` 로 부르고 commerce 가 `aud=modu-admin` + `ROLE_ADMIN` 을 다시 검증한다. 쓰기는 RW(master) 풀, 읽기는 RO(replica) 풀. 삭제는 `deleted_at` + `@SQLRestriction`. 앱은 토큰 갱신을 `TokenRefresher` 한 곳에 모은다.

**Tech Stack:** Kotlin · Spring Boot 3.5.4 · Hibernate 6.6 · QueryDSL 5.1 (commerce) / Spring Cloud Gateway · Boot 3.4.2 (gateway) / React 19 · Vite · vitest (admin) / Android Kotlin · OkHttp 4.2.1 · Glide 4.12 (app)

**Spec:** `docs/superpowers/specs/2026-09-14-product-crud-design.md`

## Global Constraints

- 커밋만 한다. **푸시하지 않는다.**
- 저장소: `modu_commerce`(브랜치 `feature/product-crud`), `modu_messenger`(브랜치 `feature/commerce-products-admin`).
- commerce 테스트: `cd backend/commerce-service && ./gradlew test` (시작 시 18개 통과).
- commerce 커밋 전: `./gradlew ktlintFormat` 후 `./gradlew test ktlintCheck`. 계획서 코드의 긴 한 줄 표현식(140자 초과)은 ktlint 가 줄바꿈한다.
- 안드로이드 테스트: `cd android/modu_commerce && ANDROID_HOME=/Users/imjunseob/Library/Android/sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug` (시작 시 11개 통과).
- admin 테스트: `cd admin && npm test` (시작 시 49개 통과).
- 게이트웨이 테스트: `cd backend/gateway-service && ./gradlew test --tests com.example.gatewayservice.GatewayRoutesTest`.
- 문구·주석은 한국어, 기존 코드 스타일(ktlint, 주석 밀도)을 따른다.
- Kotlin 블록 주석은 중첩된다. KDoc 안에 `/**` (예: `/api-admin/**`)를 쓰면 주석이 안 닫힌다 — `/api-admin 이하`처럼 쓴다.
- 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>` 와 `Claude-Session: https://claude.ai/code/session_01VRCHVtJZLYHgKg2wH4Ha8G`.

## File Map

| 저장소 | 파일 | 책임 |
|---|---|---|
| commerce | `commerce-application/.../domain/entity/Product.kt` | `update`, `delete`, `deletedAt`, `@SQLRestriction` |
| commerce | `.../domain/repository/rw/ProductRwRepository.kt` | `countIncludingDeleted()` (시더용 네이티브 카운트) |
| commerce | `.../domain/repository/ro/ProductCustomRepository(Impl).kt` | `searchPage(keyword, pageable)` |
| commerce | `.../service/ProductQueryService.kt` | `searchAdminProducts(keyword, page, size)` |
| commerce | `.../service/ProductCommandService.kt` (new) | 등록·수정·삭제 (RW) |
| commerce | `.../usecase/{Create,Update,Delete}ProductUseCase.kt`, `SearchAdminProductsUseCase.kt` (new) | 유스케이스 |
| commerce | `.../usecase/command/ProductCommand.kt`, `usecase/result/ProductPageResult.kt` (new) | 입력·페이지 결과 |
| commerce | `commerce-api/.../config/SecurityConfig.kt`, `ModuOAuthProperties.kt` | admin 체인 |
| commerce | `commerce-api/.../product/AdminProductController.kt`, `request/ProductRequest.kt`, `response/ProductPageResponse.kt` (new) | admin API |
| commerce | `commerce-api/.../common/GlobalExceptionHandler.kt` | 400 처리 |
| messenger | `backend/gateway-service/src/main/resources/application.yml` | 라우트 |
| messenger | `admin/src/api/products.ts`, `util/format.ts`, `pages/ProductsPage.tsx`, `pages/ProductFormPage.tsx`, `App.tsx`, `components/Layout.tsx`, `styles.css` | admin |
| commerce | `android/.../CommerceApiClient.kt`, `Product.kt`, `TokenRefresher.kt`, `EmptyState.kt`, `ProductAdapter.kt`, `ProductListActivity.kt`, `ProductDetailActivity.kt` + res | 앱 |

---
### Task 1: 상품 소프트 삭제·수정 (도메인)

**Files:**
- Modify: `backend/commerce-service/commerce-application/src/main/kotlin/com/example/commerce/application/domain/entity/Product.kt`
- Modify: `.../domain/repository/rw/ProductRwRepository.kt`
- Modify: `.../seed/ProductSeeder.kt`
- Test: `.../test/.../domain/entity/ProductTest.kt`, `.../domain/repository/ProductRepositoryTest.kt`, `.../seed/ProductSeederTest.kt`

**Interfaces:**
- Produces: `Product.update(name: String, description: String, price: Long, imageUrl: String?)`, `Product.delete(now: LocalDateTime = LocalDateTime.now())`, `Product.isDeleted(): Boolean`, `Product.deletedAt: LocalDateTime?`, `ProductRwRepository.countIncludingDeleted(): Long`

- [ ] **Step 1: 실패하는 테스트 작성**

`ProductTest.kt` 에 추가:
```kotlin
    @Test
    fun `update는 이름 설명 가격 이미지를 바꾼다`() {
        val product = Product.create(name = "텀블러", description = "차가운", price = 24_000)

        product.update(name = "텀블러 L", description = "더 큰", price = 29_000, imageUrl = "https://img/t.png")

        assertEquals("텀블러 L", product.name)
        assertEquals("더 큰", product.description)
        assertEquals(29_000L, product.price)
        assertEquals("https://img/t.png", product.imageUrl)
    }

    @Test
    fun `delete는 삭제 시각만 남긴다`() {
        val product = Product.create(name = "텀블러", description = "차가운", price = 24_000)
        val now = java.time.LocalDateTime.of(2026, 9, 14, 12, 0)

        product.delete(now)

        assertEquals(now, product.deletedAt)
        assertEquals(true, product.isDeleted())
    }
```

`ProductRepositoryTest.kt` 에 추가:
```kotlin
        @Test
        fun `삭제한 상품은 RO 조회 검색 단건에서 빠진다`() {
            val kept = productRwRepository.save(Product.create(name = "모두 키보드", description = "저소음", price = 1))
            val gone = productRwRepository.save(Product.create(name = "모두 키캡", description = "저소음", price = 2))
            gone.delete()
            productRwRepository.save(gone)

            assertEquals(listOf(kept.id), productRoRepository.findAllByOrderByIdAsc().map { it.id })
            assertEquals(listOf("모두 키보드"), productRoRepository.search("저소음").map { it.name })
            assertEquals(null, productRoRepository.findById(gone.id!!))
        }

        @Test
        fun `countIncludingDeleted는 삭제한 행도 센다`() {
            val gone = productRwRepository.save(Product.create(name = "모두 키캡", description = "", price = 2))
            gone.delete()
            productRwRepository.save(gone)

            assertEquals(0L, productRwRepository.count())
            assertTrue(productRwRepository.countIncludingDeleted() >= 1L)
        }
```

`ProductSeederTest.kt` 를 스프링 테스트로 바꾸지 않고 새 파일 `ProductSeederRunTest.kt` 추가:
```kotlin
package com.example.commerce.application.seed

import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductSeederRunTest
    @Autowired
    constructor(
        private val productSeeder: ProductSeeder,
        private val productRwRepository: ProductRwRepository,
    ) {
        /** 관리자가 상품을 전부 지웠다고 재기동 때 테스트 상품이 되살아나면 안 된다. */
        @Test
        fun `삭제된 상품만 남아 있으면 다시 넣지 않는다`() {
            productRwRepository.deleteAll()
            val gone = productRwRepository.save(Product.create(name = "지운 상품", description = "", price = 1))
            gone.delete()
            productRwRepository.save(gone)

            productSeeder.run(DefaultApplicationArguments())

            assertEquals(0L, productRwRepository.count())
        }
    }
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend/commerce-service && ./gradlew :commerce-application:test`
Expected: 컴파일 실패 — `Unresolved reference: update`, `delete`, `countIncludingDeleted`

- [ ] **Step 3: 구현**

`Product.kt` 전체:
```kotlin
package com.example.commerce.application.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 삭제는 deleted_at 만 채운다(소프트 삭제). @SQLRestriction 이 조회·검색·단건 어디서든
 * 삭제된 행을 빼므로 쿼리마다 조건을 넣지 않는다. 삭제 행까지 세야 하면 네이티브 쿼리를 쓴다.
 */
@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
class Product(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    @Column(name = "description", nullable = false, length = 500)
    var description: String,
    /** 원 단위 가격 */
    @Column(name = "price", nullable = false)
    var price: Long,
    @Column(name = "image_url", length = 500)
    var imageUrl: String? = null,
) : BaseEntity() {
    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    fun update(
        name: String,
        description: String,
        price: Long,
        imageUrl: String?,
    ) {
        this.name = name
        this.description = description
        this.price = price
        this.imageUrl = imageUrl
    }

    fun delete(now: LocalDateTime = LocalDateTime.now()) {
        deletedAt = now
    }

    fun isDeleted(): Boolean = deletedAt != null

    override fun toString(): String = "Product(id=$id, name='$name', price=$price)"

    companion object {
        fun create(
            name: String,
            description: String,
            price: Long,
            imageUrl: String? = null,
        ): Product =
            Product(
                name = name,
                description = description,
                price = price,
                imageUrl = imageUrl,
            )
    }
}
```

`ProductRwRepository.kt` 전체:
```kotlin
package com.example.commerce.application.domain.repository.rw

import com.example.commerce.application.config.RwRepository
import com.example.commerce.application.domain.entity.Product
import org.springframework.data.jpa.repository.Query

interface ProductRwRepository : RwRepository<Product, Long> {
    /** @SQLRestriction 을 우회해 삭제된 행까지 센다. 시더가 "한 번이라도 상품이 있었는지" 볼 때 쓴다. */
    @Query(value = "select count(*) from products", nativeQuery = true)
    fun countIncludingDeleted(): Long
}
```

`ProductSeeder.run` 의 조건만 바꾼다:
```kotlin
        if (productRwRepository.countIncludingDeleted() > 0) return
```
그리고 클래스 KDoc 을 `/** 테스트용 상품 4개. 삭제된 행까지 포함해 테이블이 비어 있을 때만 넣는다. */` 로.

- [ ] **Step 4: 통과 확인**

Run: `cd backend/commerce-service && ./gradlew test`
Expected: BUILD SUCCESSFUL, 23개 통과 (18 + 5)

- [ ] **Step 5: 커밋**

```bash
git add backend/commerce-service/commerce-application
git commit -m "Feat: 상품 소프트 삭제·수정 도메인" # + 본문, 공동 저자 줄
```

### Task 2: admin 상품 페이지 검색 (RO)

**Files:**
- Modify: `commerce-application/.../domain/repository/ro/ProductCustomRepository.kt`, `ProductCustomRepositoryImpl.kt`
- Modify: `.../service/ProductQueryService.kt`
- Create: `.../usecase/result/ProductPageResult.kt`, `.../usecase/SearchAdminProductsUseCase.kt`
- Test: `.../domain/repository/ProductRepositoryTest.kt`, `.../service/ProductQueryServiceTest.kt`

**Interfaces:**
- Consumes: Task 1 의 `Product.delete()`
- Produces: `ProductCustomRepository.searchPage(keyword: String?, pageable: Pageable): Page<Product>`, `ProductQueryService.searchAdminProducts(keyword: String?, page: Int, size: Int): Page<Product>`, `ProductPageResult(content: List<GetProductResult>, totalElements: Long, totalPages: Int, number: Int, size: Int)` + `ProductPageResult.from(page: Page<Product>)`, `SearchAdminProductsUseCase.execute(keyword: String?, page: Int, size: Int): ProductPageResult`

- [ ] **Step 1: 실패하는 테스트 작성**

`ProductRepositoryTest.kt` 에 추가 (import `org.springframework.data.domain.PageRequest`):
```kotlin
        @Test
        fun `searchPage는 최신 등록순으로 자르고 삭제한 상품은 세지 않는다`() {
            val a = productRwRepository.save(Product.create(name = "모두 A", description = "", price = 1))
            val b = productRwRepository.save(Product.create(name = "모두 B", description = "", price = 2))
            val c = productRwRepository.save(Product.create(name = "모두 C", description = "저소음", price = 3))
            val gone = productRwRepository.save(Product.create(name = "모두 D", description = "", price = 4))
            gone.delete()
            productRwRepository.save(gone)

            val first = productRoRepository.searchPage(null, PageRequest.of(0, 2))
            assertEquals(listOf(c.id, b.id), first.content.map { it.id })
            assertEquals(3L, first.totalElements)
            assertEquals(2, first.totalPages)

            assertEquals(listOf(a.id), productRoRepository.searchPage("  ", PageRequest.of(1, 2)).content.map { it.id })
            assertEquals(listOf("모두 C"), productRoRepository.searchPage("저소음", PageRequest.of(0, 10)).content.map { it.name })
        }
```

`ProductQueryServiceTest.kt` 에 추가:
```kotlin
        @Test
        fun `admin 페이지 검색은 페이지 번호와 크기를 안전한 범위로 자른다`() {
            val clamped = productQueryService.searchAdminProducts(null, -3, 1000)
            assertEquals(0, clamped.number)
            assertEquals(100, clamped.size)
            assertEquals("모두 기계식 키보드", clamped.content.first().name)

            assertEquals(1, productQueryService.searchAdminProducts(null, 0, 0).size)
        }
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend/commerce-service && ./gradlew :commerce-application:test`
Expected: 컴파일 실패 — `Unresolved reference: searchPage`, `searchAdminProducts`

- [ ] **Step 3: 구현**

`ProductCustomRepository.kt`:
```kotlin
package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.domain.entity.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductCustomRepository {
    /** 이름 또는 설명에 keyword 가 들어간 상품을 id 순으로 */
    fun search(keyword: String): List<Product>

    /** 백오피스 목록. 최신 등록(id 내림차순)부터. keyword 가 비면 전체. */
    fun searchPage(
        keyword: String?,
        pageable: Pageable,
    ): Page<Product>
}
```

`ProductCustomRepositoryImpl.kt`:
```kotlin
package com.example.commerce.application.domain.repository.ro

import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.QProduct.product
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class ProductCustomRepositoryImpl(
    @Qualifier("roQueryFactory") private val queryFactory: JPAQueryFactory,
) : ProductCustomRepository {
    override fun search(keyword: String): List<Product> =
        queryFactory
            .selectFrom(product)
            .where(matches(keyword))
            .orderBy(product.id.asc())
            .fetch()

    override fun searchPage(
        keyword: String?,
        pageable: Pageable,
    ): Page<Product> {
        val condition = keyword?.trim()?.takeIf { it.isNotEmpty() }?.let(::matches)
        val content =
            queryFactory
                .selectFrom(product)
                .where(condition)
                .orderBy(product.id.desc())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()
        val total =
            queryFactory
                .select(product.count())
                .from(product)
                .where(condition)
                .fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    private fun matches(keyword: String): BooleanExpression = product.name.contains(keyword).or(product.description.contains(keyword))
}
```

`ProductQueryService.kt` 에 추가 (import `org.springframework.data.domain.Page`, `org.springframework.data.domain.PageRequest`):
```kotlin
    /** page 는 0 이상, size 는 1~100 으로 자른다. 잘못된 쿼리 파라미터로 전체 테이블을 긁지 않게. */
    fun searchAdminProducts(
        keyword: String?,
        page: Int,
        size: Int,
    ): Page<Product> = productRoRepository.searchPage(keyword, PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE)))

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
```

`usecase/result/ProductPageResult.kt`:
```kotlin
package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.Product
import org.springframework.data.domain.Page

/** Spring 의 PageImpl 을 그대로 직렬화하지 않도록 필요한 값만 옮긴다. */
data class ProductPageResult(
    val content: List<GetProductResult>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
) {
    companion object {
        fun from(page: Page<Product>) =
            ProductPageResult(
                content = page.content.map(GetProductResult::from),
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                number = page.number,
                size = page.size,
            )
    }
}
```

`usecase/SearchAdminProductsUseCase.kt`:
```kotlin
package com.example.commerce.application.usecase

import com.example.commerce.application.service.ProductQueryService
import com.example.commerce.application.usecase.result.ProductPageResult
import org.springframework.stereotype.Component

@Component
class SearchAdminProductsUseCase(
    private val productQueryService: ProductQueryService,
) {
    fun execute(
        keyword: String?,
        page: Int,
        size: Int,
    ): ProductPageResult = ProductPageResult.from(productQueryService.searchAdminProducts(keyword, page, size))
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend/commerce-service && ./gradlew test`
Expected: BUILD SUCCESSFUL, 25개 통과

- [ ] **Step 5: 커밋** — `Feat: admin 상품 페이지 검색(RO)`

### Task 3: 등록·수정·삭제 명령 (RW)

**Files:**
- Create: `commerce-application/.../usecase/command/ProductCommand.kt`
- Create: `.../service/ProductCommandService.kt`
- Create: `.../usecase/CreateProductUseCase.kt`, `UpdateProductUseCase.kt`, `DeleteProductUseCase.kt`
- Test: `.../service/ProductCommandServiceTest.kt`

**Interfaces:**
- Consumes: Task 1 `Product.update/delete/isDeleted`, Task 2 `ProductQueryService.searchAdminProducts`
- Produces: `ProductCommand(name: String, description: String, price: Long, imageUrl: String?)`, `CreateProductUseCase.execute(command): GetProductResult`, `UpdateProductUseCase.execute(id: Long, command): GetProductResult`, `DeleteProductUseCase.execute(id: Long)` — 없거나 삭제된 id 는 `EntityNotFoundException`

- [ ] **Step 1: 실패하는 테스트 작성** — `service/ProductCommandServiceTest.kt`
```kotlin
package com.example.commerce.application.service

import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.usecase.CreateProductUseCase
import com.example.commerce.application.usecase.DeleteProductUseCase
import com.example.commerce.application.usecase.UpdateProductUseCase
import com.example.commerce.application.usecase.command.ProductCommand
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductCommandServiceTest
    @Autowired
    constructor(
        private val createProductUseCase: CreateProductUseCase,
        private val updateProductUseCase: UpdateProductUseCase,
        private val deleteProductUseCase: DeleteProductUseCase,
        private val productQueryService: ProductQueryService,
        private val productRwRepository: ProductRwRepository,
    ) {
        private val umbrella = ProductCommand(name = "모두 우산", description = "자동 우산", price = 15_000, imageUrl = null)

        @BeforeEach
        fun cleanUp() {
            productRwRepository.deleteAll()
        }

        @Test
        fun `등록한 상품은 RO 로 조회된다`() {
            val created = createProductUseCase.execute(umbrella)

            assertEquals("모두 우산", created.name)
            assertEquals("자동 우산", productQueryService.findProduct(created.id).description)
        }

        @Test
        fun `수정하면 모든 필드가 바뀐다`() {
            val created = createProductUseCase.execute(umbrella)

            val updated = updateProductUseCase.execute(created.id, ProductCommand("모두 장우산", "튼튼한", 21_000, "https://img/u.png"))

            assertEquals(created.id, updated.id)
            val found = productQueryService.findProduct(created.id)
            assertEquals(listOf("모두 장우산", "튼튼한", "https://img/u.png"), listOf(found.name, found.description, found.imageUrl))
            assertEquals(21_000L, found.price)
        }

        @Test
        fun `삭제하면 앱 조회와 admin 목록에서 사라지고 다시 수정 삭제할 수 없다`() {
            val created = createProductUseCase.execute(umbrella)

            deleteProductUseCase.execute(created.id)

            assertEquals(emptyList<Long>(), productQueryService.findProducts(null).map { it.id })
            assertEquals(0L, productQueryService.searchAdminProducts(null, 0, 15).totalElements)
            assertThrows(EntityNotFoundException::class.java) { productQueryService.findProduct(created.id) }
            assertThrows(EntityNotFoundException::class.java) { deleteProductUseCase.execute(created.id) }
            assertThrows(EntityNotFoundException::class.java) { updateProductUseCase.execute(created.id, umbrella) }
        }

        @Test
        fun `없는 상품은 수정 삭제할 수 없다`() {
            assertThrows(EntityNotFoundException::class.java) { updateProductUseCase.execute(999_999L, umbrella) }
            assertThrows(EntityNotFoundException::class.java) { deleteProductUseCase.execute(999_999L) }
        }
    }
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend/commerce-service && ./gradlew :commerce-application:test`
Expected: 컴파일 실패 — `Unresolved reference: CreateProductUseCase`

- [ ] **Step 3: 구현**

`usecase/command/ProductCommand.kt`:
```kotlin
package com.example.commerce.application.usecase.command

/** 등록·수정 입력. 형식 검증(길이·범위·URL)은 api 모듈의 요청 DTO 가 끝낸 값이다. */
data class ProductCommand(
    val name: String,
    val description: String,
    val price: Long,
    val imageUrl: String?,
)
```

`service/ProductCommandService.kt`:
```kotlin
package com.example.commerce.application.service

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.usecase.command.ProductCommand
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 쓰기는 전부 master(RW). 등록·수정 결과는 방금 쓴 RW 엔티티로 돌려준다 —
 * 레플리카에서 다시 읽으면 복제 지연 동안 옛값이 나올 수 있다.
 */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class ProductCommandService(
    private val productRwRepository: ProductRwRepository,
) {
    fun create(command: ProductCommand): Product =
        productRwRepository.save(
            Product.create(name = command.name, description = command.description, price = command.price, imageUrl = command.imageUrl),
        )

    fun update(
        id: Long,
        command: ProductCommand,
    ): Product = findActive(id).apply { update(command.name, command.description, command.price, command.imageUrl) }

    fun delete(id: Long) {
        findActive(id).delete()
    }

    /** @SQLRestriction 이 걸려 있지만, 같은 트랜잭션에서 이미 로드된 엔티티도 있으니 삭제 여부를 한 번 더 본다. */
    private fun findActive(id: Long): Product =
        productRwRepository.findById(id).orElse(null)?.takeUnless { it.isDeleted() }
            ?: run {
                logger.error { "Product not found: $id" }
                throw EntityNotFoundException("id: $id 에 해당하는 상품이 없습니다.")
            }
}
```

`usecase/CreateProductUseCase.kt`:
```kotlin
package com.example.commerce.application.usecase

import com.example.commerce.application.service.ProductCommandService
import com.example.commerce.application.usecase.command.ProductCommand
import com.example.commerce.application.usecase.result.GetProductResult
import org.springframework.stereotype.Component

@Component
class CreateProductUseCase(
    private val productCommandService: ProductCommandService,
) {
    fun execute(command: ProductCommand): GetProductResult = GetProductResult.from(productCommandService.create(command))
}
```

`usecase/UpdateProductUseCase.kt`:
```kotlin
package com.example.commerce.application.usecase

import com.example.commerce.application.service.ProductCommandService
import com.example.commerce.application.usecase.command.ProductCommand
import com.example.commerce.application.usecase.result.GetProductResult
import org.springframework.stereotype.Component

@Component
class UpdateProductUseCase(
    private val productCommandService: ProductCommandService,
) {
    fun execute(
        id: Long,
        command: ProductCommand,
    ): GetProductResult = GetProductResult.from(productCommandService.update(id, command))
}
```

`usecase/DeleteProductUseCase.kt`:
```kotlin
package com.example.commerce.application.usecase

import com.example.commerce.application.service.ProductCommandService
import org.springframework.stereotype.Component

@Component
class DeleteProductUseCase(
    private val productCommandService: ProductCommandService,
) {
    fun execute(id: Long) = productCommandService.delete(id)
}
```

- [ ] **Step 4: 통과 확인** — `./gradlew test`, 29개 통과
- [ ] **Step 5: 커밋** — `Feat: 상품 등록·수정·삭제 명령(RW)`

### Task 4: admin 보안 체인 (aud=modu-admin + ROLE_ADMIN)

**Files:**
- Modify: `commerce-api/src/main/kotlin/com/example/commerce/api/config/SecurityConfig.kt`, `ModuOAuthProperties.kt`
- Modify: `commerce-api/src/main/resources/application.yml` (`modu.oauth.admin-audience`)
- Test: `commerce-api/src/test/.../config/SecurityConfigTest.kt`, `.../product/AdminSecurityTest.kt`

**Interfaces:**
- Produces: `SecurityConfig.ADMIN_ROLE = "ROLE_ADMIN"`, `SecurityConfig.tokenValidator(issuer: String, audience: String): OAuth2TokenValidator<Jwt>`, `SecurityConfig.rolesAuthenticationConverter(): JwtAuthenticationConverter`, `ModuOAuthProperties.adminAudience: String = "modu-admin"`

- [ ] **Step 1: 실패하는 테스트 작성**

`config/SecurityConfigTest.kt`:
```kotlin
package com.example.commerce.api.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

class SecurityConfigTest {
    private val issuer = "http://auth.test/auth-service"

    private fun jwt(
        aud: String,
        roles: List<String>? = null,
    ): Jwt =
        Jwt
            .withTokenValue("t")
            .header("alg", "RS256")
            .issuer(issuer)
            .subject("admin")
            .audience(listOf(aud))
            .apply { if (roles != null) claim("roles", roles) }
            .build()

    @Test
    fun `admin 검증기는 백오피스 토큰만 통과시킨다`() {
        val validator = SecurityConfig.tokenValidator(issuer, "modu-admin")

        assertFalse(validator.validate(jwt("modu-admin")).hasErrors())
        assertTrue(validator.validate(jwt("modu-commerce")).hasErrors())
    }

    @Test
    fun `roles 클레임을 접두사 없이 권한으로 옮긴다`() {
        val converter = SecurityConfig.rolesAuthenticationConverter()

        val admin = converter.convert(jwt("modu-admin", listOf("ROLE_ADMIN")))!!.authorities.map { it.authority }
        val user = converter.convert(jwt("modu-admin", listOf("ROLE_USER")))!!.authorities.map { it.authority }

        assertTrue(SecurityConfig.ADMIN_ROLE in admin)
        assertFalse(SecurityConfig.ADMIN_ROLE in user)
    }
}
```

`product/AdminSecurityTest.kt`:
```kotlin
package com.example.commerce.api.product

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @Test
        fun `토큰이 없으면 401`() {
            mockMvc.get("/api-admin/v1/products").andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `ROLE_ADMIN 이 없으면 403`() {
            mockMvc
                .get("/api-admin/v1/products") { with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))) }
                .andExpect { status { isForbidden() } }
        }
    }
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend/commerce-service && ./gradlew :commerce-api:test`
Expected: 컴파일 실패 — `Unresolved reference: tokenValidator`

- [ ] **Step 3: 구현**

`ModuOAuthProperties.kt`:
```kotlin
package com.example.commerce.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 모두 계정(auth-service) 토큰 검증 설정. 커머스 앱은 aud=modu-commerce 토큰을,
 * 백오피스(/api-admin 이하)는 aud=modu-admin 토큰을 쓴다.
 */
@ConfigurationProperties("modu.oauth")
data class ModuOAuthProperties(
    val issuer: String,
    val jwksUri: String,
    val audience: String = "modu-commerce",
    val adminAudience: String = "modu-admin",
)
```

`SecurityConfig.kt`:
```kotlin
package com.example.commerce.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

/** 모든 API 는 auth-service 가 발급한 RS256 토큰(JWKS 검증, iss/aud 확인)이 있어야 한다. */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    /**
     * 백오피스 전용. 게이트웨이도 같은 검사를 하지만 8200 포트로 직접 오는 요청이 있으므로
     * 여기서 aud=modu-admin 과 roles 의 ROLE_ADMIN 을 다시 본다.
     */
    @Bean
    @Order(1)
    fun adminSecurityFilterChain(
        http: HttpSecurity,
        props: ModuOAuthProperties,
    ): SecurityFilterChain =
        http
            .securityMatcher("/api-admin/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().hasAuthority(ADMIN_ROLE) }
            .oauth2ResourceServer {
                it.jwt { jwt ->
                    jwt.decoder(decoder(props.jwksUri, tokenValidator(props.issuer, props.adminAudience)))
                    jwt.jwtAuthenticationConverter(rolesAuthenticationConverter())
                }
            }.build()

    @Bean
    @Order(2)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { it.jwt {} }
            .build()

    /** 앱 체인이 쓰는 기본 디코더(aud=modu-commerce). admin 체인은 자기 디코더를 따로 만든다. */
    @Bean
    fun jwtDecoder(props: ModuOAuthProperties): JwtDecoder = decoder(props.jwksUri, tokenValidator(props.issuer, props.audience))

    companion object {
        const val ADMIN_ROLE = "ROLE_ADMIN"

        fun tokenValidator(
            issuer: String,
            audience: String,
        ): OAuth2TokenValidator<Jwt> = DelegatingOAuth2TokenValidator(JwtValidators.createDefaultWithIssuer(issuer), AudienceValidator(audience))

        /** auth-service 는 roles 에 "ROLE_ADMIN" 처럼 접두사까지 넣어 준다. 그대로 권한으로 쓴다. */
        fun rolesAuthenticationConverter(): JwtAuthenticationConverter =
            JwtAuthenticationConverter().apply {
                setJwtGrantedAuthoritiesConverter(
                    JwtGrantedAuthoritiesConverter().apply {
                        setAuthoritiesClaimName("roles")
                        setAuthorityPrefix("")
                    },
                )
            }

        private fun decoder(
            jwksUri: String,
            validator: OAuth2TokenValidator<Jwt>,
        ): JwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build().apply { setJwtValidator(validator) }
    }
}
```

`application.yml` 의 `modu.oauth` 에 `audience: modu-commerce` 아래 줄 추가:
```yaml
    # 백오피스(/api-admin/**) 토큰의 aud. 게이트웨이 AuthorizationHeaderFilter=ROLE_ADMIN,modu-admin 과 같은 값.
    admin-audience: modu-admin
```

- [ ] **Step 4: 통과 확인** — `./gradlew test`, 33개 통과 (앱 체인 기존 테스트 포함)
- [ ] **Step 5: 커밋** — `Feat: commerce admin 보안 체인(aud=modu-admin, ROLE_ADMIN)`

### Task 5: admin 상품 컨트롤러 · 입력 검증 · README

**Files:**
- Modify: `commerce-api/build.gradle.kts` (`spring-boot-starter-validation`)
- Create: `commerce-api/.../product/AdminProductController.kt`, `product/request/ProductRequest.kt`, `product/response/ProductPageResponse.kt`
- Modify: `commerce-api/.../common/GlobalExceptionHandler.kt`
- Modify: `README.md` (커머스 서비스 절)
- Test: `commerce-api/src/test/.../product/AdminProductControllerTest.kt`

**Interfaces:**
- Consumes: Task 2 `SearchAdminProductsUseCase`, Task 3 `Create/Update/DeleteProductUseCase`, 기존 `GetProductUseCase`, `GetProductResponse`
- Produces (HTTP): `GET /api-admin/v1/products?q=&page=&size=` → `{content,totalElements,totalPages,number,size}`, `GET/PUT/DELETE /api-admin/v1/products/{id}`, `POST /api-admin/v1/products` → 201. 오류 본문 `{"message": "<필드>: <이유>"}`

- [ ] **Step 1: 실패하는 테스트 작성** — `product/AdminProductControllerTest.kt`
```kotlin
package com.example.commerce.api.product

import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import com.example.commerce.application.seed.ProductSeeder
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@SpringBootTest
@AutoConfigureMockMvc
class AdminProductControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val productRwRepository: ProductRwRepository,
    ) {
        private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

        /** 같은 스프링 컨텍스트(H2)를 ProductControllerTest 와 나눠 쓰므로 시드 4개 상태로 되돌려 둔다. */
        @BeforeEach
        @AfterEach
        fun reseed() {
            productRwRepository.deleteAll()
            productRwRepository.saveAll(ProductSeeder.sampleProducts())
        }

        @Test
        fun `목록은 최신 등록순 페이지로 준다`() {
            mockMvc
                .get("/api-admin/v1/products") {
                    param("page", "0")
                    param("size", "2")
                    with(admin)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.content.length()") { value(2) }
                    jsonPath("$.content[0].name") { value("모두 기계식 키보드") }
                    jsonPath("$.totalElements") { value(4) }
                    jsonPath("$.totalPages") { value(2) }
                    jsonPath("$.number") { value(0) }
                    jsonPath("$.size") { value(2) }
                }
        }

        @Test
        fun `등록 수정 삭제 흐름`() {
            val created =
                mockMvc
                    .post("/api-admin/v1/products") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"name":"모두 우산","description":"자동 우산","price":15000,"imageUrl":"https://img/u.png"}"""
                    }.andExpect {
                        status { isCreated() }
                        header { exists("Location") }
                        jsonPath("$.name") { value("모두 우산") }
                    }.andReturn()
            val id = JsonPath.read<Int>(created.response.contentAsString, "$.id")

            mockMvc
                .put("/api-admin/v1/products/$id") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"모두 장우산","description":"튼튼한","price":21000,"imageUrl":null}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.name") { value("모두 장우산") }
                    jsonPath("$.imageUrl") { isEmpty() }
                }

            mockMvc.delete("/api-admin/v1/products/$id") { with(admin) }.andExpect { status { isNoContent() } }
            mockMvc.get("/api-admin/v1/products/$id") { with(admin) }.andExpect { status { isNotFound() } }
            mockMvc.get("/api/v1/products/$id") { with(jwt()) }.andExpect { status { isNotFound() } }
            mockMvc.delete("/api-admin/v1/products/$id") { with(admin) }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `검증에 실패하면 400 과 필드별 이유`() {
            fun postExpecting(
                body: String,
                message: String,
            ) {
                mockMvc
                    .post("/api-admin/v1/products") {
                        with(admin)
                        contentType = MediaType.APPLICATION_JSON
                        content = body
                    }.andExpect {
                        status { isBadRequest() }
                        jsonPath("$.message") { value(message) }
                    }
            }

            postExpecting("""{"name":"  ","price":1}""", "name: 상품 이름을 입력해 주세요.")
            postExpecting("""{"name":"우산","price":-1}""", "price: 가격은 0 이상이어야 합니다.")
            postExpecting("""{"name":"우산"}""", "price: 가격을 입력해 주세요.")
            postExpecting("""{"name":"우산","price":1,"imageUrl":"ftp://img/u.png"}""", "imageUrl: 이미지 주소는 http:// 또는 https:// 로 시작해야 합니다.")
            postExpecting("not json", "요청 본문을 읽을 수 없습니다.")
        }

        @Test
        fun `빈 설명과 빈 이미지 주소는 빈 문자열과 null 로 저장한다`() {
            mockMvc
                .post("/api-admin/v1/products") {
                    with(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":" 모두 우산 ","price":0,"imageUrl":""}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.name") { value("모두 우산") }
                    jsonPath("$.description") { value("") }
                    jsonPath("$.imageUrl") { isEmpty() }
                }
        }
    }
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend/commerce-service && ./gradlew :commerce-api:test --tests '*AdminProductControllerTest'`
Expected: FAIL — 목록은 404(핸들러 없음), 나머지도 201/400 대신 404

- [ ] **Step 3: 구현**

`commerce-api/build.gradle.kts` 의 `spring-boot-starter-web` 아래:
```kotlin
    // admin 상품 등록·수정 요청 검증(@Valid)
    implementation("org.springframework.boot:spring-boot-starter-validation")
```

`product/request/ProductRequest.kt`:
```kotlin
package com.example.commerce.api.product.request

import com.example.commerce.application.usecase.command.ProductCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

/** 백오피스 등록·수정 본문. 빠진 필드도 400 메시지로 알려 주려고 전부 nullable 로 받는다. */
data class ProductRequest(
    @field:NotBlank(message = "상품 이름을 입력해 주세요.")
    @field:Size(max = 100, message = "상품 이름은 100자 이하여야 합니다.")
    val name: String? = null,
    @field:Size(max = 500, message = "설명은 500자 이하여야 합니다.")
    val description: String? = null,
    @field:NotNull(message = "가격을 입력해 주세요.")
    @field:PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
    val price: Long? = null,
    @field:Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
    @field:Pattern(regexp = "^(https?://\\S+)?$", message = "이미지 주소는 http:// 또는 https:// 로 시작해야 합니다.")
    val imageUrl: String? = null,
) {
    /** @Valid 를 통과한 뒤에만 부른다. */
    fun toCommand(): ProductCommand =
        ProductCommand(
            name = requireNotNull(name).trim(),
            description = description?.trim().orEmpty(),
            price = requireNotNull(price),
            imageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() },
        )
}
```

`product/response/ProductPageResponse.kt`:
```kotlin
package com.example.commerce.api.product.response

import com.example.commerce.application.usecase.result.ProductPageResult

/** 백오피스의 Page<T> 타입(content/totalElements/totalPages/number/size)과 같은 모양. */
data class ProductPageResponse(
    val content: List<GetProductResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
) {
    companion object {
        fun from(result: ProductPageResult) =
            ProductPageResponse(
                content = result.content.map(GetProductResponse::from),
                totalElements = result.totalElements,
                totalPages = result.totalPages,
                number = result.number,
                size = result.size,
            )
    }
}
```

`product/AdminProductController.kt`:
```kotlin
package com.example.commerce.api.product

import com.example.commerce.api.product.request.ProductRequest
import com.example.commerce.api.product.response.GetProductResponse
import com.example.commerce.api.product.response.ProductPageResponse
import com.example.commerce.application.usecase.CreateProductUseCase
import com.example.commerce.application.usecase.DeleteProductUseCase
import com.example.commerce.application.usecase.GetProductUseCase
import com.example.commerce.application.usecase.SearchAdminProductsUseCase
import com.example.commerce.application.usecase.UpdateProductUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** 백오피스가 게이트웨이(/commerce-service/api-admin 이하)를 거쳐 부른다. 권한은 SecurityConfig 의 admin 체인이 본다. */
@RestController
@RequestMapping("/api-admin/v1/products")
class AdminProductController(
    private val searchAdminProductsUseCase: SearchAdminProductsUseCase,
    private val getProductUseCase: GetProductUseCase,
    private val createProductUseCase: CreateProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
) {
    @GetMapping
    fun products(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<ProductPageResponse> = ResponseEntity.ok(ProductPageResponse.from(searchAdminProductsUseCase.execute(q, page, size)))

    @GetMapping("/{id}")
    fun product(
        @PathVariable id: Long,
    ): ResponseEntity<GetProductResponse> = ResponseEntity.ok(GetProductResponse.from(getProductUseCase.execute(id)))

    @PostMapping
    fun create(
        @Valid @RequestBody request: ProductRequest,
    ): ResponseEntity<GetProductResponse> {
        val created = createProductUseCase.execute(request.toCommand())
        return ResponseEntity.created(URI.create("/api-admin/v1/products/${created.id}")).body(GetProductResponse.from(created))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: ProductRequest,
    ): ResponseEntity<GetProductResponse> = ResponseEntity.ok(GetProductResponse.from(updateProductUseCase.execute(id, request.toCommand())))

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteProductUseCase.execute(id)
        return ResponseEntity.noContent().build()
    }
}
```

`common/GlobalExceptionHandler.kt` 에 두 핸들러 추가 (import `org.springframework.http.converter.HttpMessageNotReadableException`, `org.springframework.web.bind.MethodArgumentNotValidException`):
```kotlin
    /** 여러 필드가 틀려도 첫 번째 하나만 알린다. 백오피스 폼이 한 줄로 보여 준다. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.let { "${it.field}: ${it.defaultMessage}" } ?: "요청 값이 올바르지 않습니다."
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("요청 본문을 읽을 수 없습니다."))
```

`README.md` 의 `commerce-api` 줄을 교체:
```markdown
- `commerce-api`: 실행 모듈.
  - 앱(`aud=modu-commerce`): `GET /api/v1/products`(`?q=` 검색), `GET /api/v1/products/{id}`.
  - 백오피스(`aud=modu-admin` + `roles` 에 `ROLE_ADMIN`): `GET /api-admin/v1/products?q=&page=&size=`, `GET·PUT·DELETE /api-admin/v1/products/{id}`, `POST /api-admin/v1/products`. 게이트웨이의 `/commerce-service/api-admin/**` 로 들어오며, commerce 도 토큰을 다시 검증합니다. 삭제는 `deleted_at` 만 채우는 소프트 삭제입니다.
  - 모든 토큰은 모두의 채팅 auth-service 가 발급한 RS256 토큰을 JWKS 로 검증합니다.
```
그리고 `# 테스트 18개` 를 Step 4 에서 확인한 개수로 바꾼다.

- [ ] **Step 4: 통과 확인** — `./gradlew test bootJar`, 37개 통과, `commerce-api/build/libs/commerce-api-0.0.1-SNAPSHOT.jar` 생성
- [ ] **Step 5: 커밋** — `Feat: admin 상품 CRUD API 와 입력 검증`

### Task 6: 게이트웨이 commerce admin 라우트 (modu_messenger)

**Files:**
- Modify: `modu_messenger/backend/gateway-service/src/main/resources/application.yml` (storage-service-admin 라우트 바로 아래)
- Test: `modu_messenger/backend/gateway-service/src/test/java/com/example/gatewayservice/GatewayRoutesTest.java`

**Interfaces:**
- Produces: 라우트 id `commerce-service-admin`, `/commerce-service/api-admin/**` → `http://commerce-service:8200/api-admin/**`

- [ ] **Step 1: 실패하는 테스트 작성** — `GatewayRoutesTest` 에 추가 (import `org.springframework.cloud.gateway.route.Route` 는 이미 있음)
```java
    @Test
    void commerceAdmin_routesToCommerceServiceByContainerName() {
        Route route = firstMatch(HttpMethod.POST, "/commerce-service/api-admin/v1/products").orElseThrow();
        assertEquals("commerce-service-admin", route.getId());
        // commerce-service 는 Eureka 에 없다. modu-infra 네트워크의 컨테이너 이름으로 간다.
        assertEquals("http://commerce-service:8200", route.getUri().toString());
    }

    @Test
    void commerceAppApi_isNotRoutedThroughGateway() {
        // 앱은 commerce-service(8200)를 직접 부른다. 게이트웨이에는 admin 계층만 연다.
        assertTrue(firstMatch(HttpMethod.GET, "/commerce-service/api/v1/products").isEmpty());
    }
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/imjunseob/workspace/modu_messenger/backend/gateway-service && ./gradlew test --tests com.example.gatewayservice.GatewayRoutesTest`
Expected: `commerceAdmin_routesToCommerceServiceByContainerName` FAIL (NoSuchElementException)

- [ ] **Step 3: 구현** — `storage-service-admin` 라우트 뒤, `# ---- WebSocket` 주석 앞에:
```yaml
        # commerce-service 는 Eureka 에 등록하지 않는 별도 저장소(modu_commerce) 서비스라 컨테이너 이름으로 간다.
        # 내부 토큰 대신 commerce 가 Authorization 헤더의 JWT(aud=modu-admin, ROLE_ADMIN)를 다시 검증한다.
        - id: commerce-service-admin
          uri: ${COMMERCE_SERVICE_URI:http://commerce-service:8200}
          predicates:
            - Path=/commerce-service/api-admin/**
          filters:
            - AuthorizationHeaderFilter=ROLE_ADMIN,modu-admin
            - RewritePath=/commerce-service/(?<segment>.*), /$\{segment}
```

- [ ] **Step 4: 통과 확인** — 같은 명령, GatewayRoutesTest 전부 통과
- [ ] **Step 5: 커밋** (modu_messenger) — `Feat: 게이트웨이에 commerce-service admin 라우트 추가`

### Task 7: admin 상품 API 모듈 · 가격 포맷 (modu_messenger/admin)

**Files:**
- Create: `admin/src/api/products.ts`, `admin/src/api/products.test.ts`, `admin/src/util/format.test.ts`
- Modify: `admin/src/util/format.ts`

**Interfaces:**
- Produces: `interface Product { id: number; name: string; description: string; price: number; imageUrl: string | null }`, `interface ProductInput { name: string; description: string; price: number; imageUrl: string | null }`, `searchProducts(keyword: string, page: number): Promise<Page<Product>>`, `getProduct(id: string): Promise<Product>`, `createProduct(input): Promise<Product>`, `updateProduct(id: string, input): Promise<Product>`, `deleteProduct(id: string): Promise<void>`, `validationMessage(err: unknown): string | null`, `formatPrice(price: number): string`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/api/products.test.ts`:
```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './client'
import { createProduct, deleteProduct, searchProducts, updateProduct, validationMessage } from './products'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status })

describe('products api', () => {
  afterEach(() => vi.restoreAllMocks())

  it('searches through the gateway admin route with page size 15', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 15 }))

    await searchProducts('텀블러', 2)

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toMatch(/\/commerce-service\/api-admin\/v1\/products\?q=%ED%85%80%EB%B8%94%EB%9F%AC&page=2&size=15$/)
    expect(init?.method ?? 'GET').toBe('GET')
  })

  it('creates, updates and deletes with the right methods and JSON bodies', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
    const input = { name: '모두 우산', description: '자동 우산', price: 15000, imageUrl: null }

    fetchMock.mockResolvedValueOnce(json({ id: 7, ...input }, 201))
    await createProduct(input)
    expect(fetchMock.mock.calls[0][1]?.method).toBe('POST')
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual(input)

    fetchMock.mockResolvedValueOnce(json({ id: 7, ...input }))
    await updateProduct('7', input)
    expect(String(fetchMock.mock.calls[1][0])).toMatch(/\/v1\/products\/7$/)
    expect(fetchMock.mock.calls[1][1]?.method).toBe('PUT')

    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    await expect(deleteProduct('7')).resolves.toBeUndefined()
    expect(fetchMock.mock.calls[2][1]?.method).toBe('DELETE')
  })

  it('reads the server message only from a 400 JSON body', () => {
    expect(validationMessage(new ApiError(400, '{"message":"price: 가격은 0 이상이어야 합니다."}'))).toBe('price: 가격은 0 이상이어야 합니다.')
    expect(validationMessage(new ApiError(500, '{"message":"boom"}'))).toBeNull()
    expect(validationMessage(new ApiError(400, 'not json'))).toBeNull()
    expect(validationMessage(new Error('x'))).toBeNull()
  })
})
```

`src/util/format.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { formatPrice } from './format'

describe('formatPrice', () => {
  it('groups thousands and appends 원', () => {
    expect(formatPrice(89000)).toBe('89,000원')
    expect(formatPrice(0)).toBe('0원')
  })
})
```

- [ ] **Step 2: 실패 확인** — `cd admin && npx vitest run src/api/products.test.ts src/util/format.test.ts` → import 실패

- [ ] **Step 3: 구현**

`src/api/products.ts`:
```ts
import { ApiError, api, PAGE_SIZE } from './client'
import type { Page } from './members'

/** commerce-service 상품. 앱과 같은 응답 모양이다. */
export interface Product {
  id: number
  name: string
  description: string
  price: number
  imageUrl: string | null
}

/** 등록·수정 본문. 이름 길이·가격 범위·이미지 주소 형식은 서버가 검증하고 400 으로 이유를 준다. */
export interface ProductInput {
  name: string
  description: string
  price: number
  imageUrl: string | null
}

const BASE = '/commerce-service/api-admin/v1/products'

export const searchProducts = (keyword: string, page: number) =>
  api<Page<Product>>(`${BASE}?q=${encodeURIComponent(keyword)}&page=${page}&size=${PAGE_SIZE}`)

export const getProduct = (id: string) => api<Product>(`${BASE}/${encodeURIComponent(id)}`)

export const createProduct = (input: ProductInput) => api<Product>(BASE, { method: 'POST', body: JSON.stringify(input) })

export const updateProduct = (id: string, input: ProductInput) =>
  api<Product>(`${BASE}/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(input) })

export const deleteProduct = (id: string) => api<void>(`${BASE}/${encodeURIComponent(id)}`, { method: 'DELETE' })

/** 400 응답 본문 {"message": "price: ..."} 에서 사람이 읽을 이유를 꺼낸다. 그 밖의 오류는 null. */
export function validationMessage(err: unknown): string | null {
  if (!(err instanceof ApiError) || err.status !== 400) return null
  try {
    const body = JSON.parse(err.message) as { message?: unknown }
    return typeof body.message === 'string' ? body.message : null
  } catch {
    return null
  }
}
```

`src/util/format.ts` 끝에 추가:
```ts
/** 89000 → "89,000원". 앱의 가격 표기와 같게 둔다. */
export function formatPrice(price: number): string {
  return `${price.toLocaleString('ko-KR')}원`
}
```

- [ ] **Step 4: 통과 확인** — `npm test`, 53개 통과
- [ ] **Step 5: 커밋** (modu_messenger) — `Feat: admin 상품 API 모듈과 가격 포맷`

### Task 8: admin 상품 목록 페이지 · 메뉴 · 라우트

**Files:**
- Create: `admin/src/pages/ProductsPage.tsx`, `admin/src/pages/ProductsPage.test.tsx`
- Modify: `admin/src/App.tsx` (`/products`), `admin/src/components/Layout.tsx` + `Layout.test.tsx` (상품 메뉴), `admin/src/styles.css`

**Interfaces:**
- Consumes: Task 7 `searchProducts`, `Product`, `formatPrice`
- Produces: 라우트 `/products`, CSS 클래스 `.product-thumb`, `.product-preview`, `a.btn`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/pages/ProductsPage.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as products from '../api/products'
import ProductsPage from './ProductsPage'

const tumbler = { id: 2, name: '모두 텀블러 500ml', description: '하루 종일 차가운', price: 24000, imageUrl: null }
const keyboard = { id: 4, name: '모두 기계식 키보드', description: '저소음 적축', price: 129000, imageUrl: 'https://img/k.png' }
const page = (content: products.Product[], totalPages = 1) => ({ content, totalElements: content.length, totalPages, number: 0, size: 15 })

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/products']}>
      <Routes>
        <Route path="/products" element={<ProductsPage />} />
        <Route path="/products/:id" element={<p>수정 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )

describe('ProductsPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('lists products with formatted prices', async () => {
    vi.spyOn(products, 'searchProducts').mockResolvedValue(page([keyboard, tumbler]))
    renderPage()

    expect(await screen.findByText('모두 기계식 키보드')).toBeInTheDocument()
    expect(screen.getByText('129,000원')).toBeInTheDocument()
    expect(screen.getByText('24,000원')).toBeInTheDocument()
  })

  it('goes back to the first page when searching', async () => {
    const search = vi.spyOn(products, 'searchProducts').mockResolvedValue(page([keyboard], 3))
    renderPage()
    await screen.findByText('모두 기계식 키보드')

    await userEvent.click(screen.getByRole('button', { name: '다음' }))
    expect(search).toHaveBeenLastCalledWith('', 1)

    await userEvent.type(screen.getByLabelText('상품 검색'), '키보드')
    await userEvent.click(screen.getByRole('button', { name: '검색' }))
    expect(search).toHaveBeenLastCalledWith('키보드', 0)
  })

  it('opens the edit page when a row is clicked', async () => {
    vi.spyOn(products, 'searchProducts').mockResolvedValue(page([tumbler]))
    renderPage()

    await userEvent.click(await screen.findByText('모두 텀블러 500ml'))

    expect(await screen.findByText('수정 화면')).toBeInTheDocument()
  })

  it('says so when nothing matches the search', async () => {
    const search = vi.spyOn(products, 'searchProducts').mockResolvedValue(page([keyboard]))
    renderPage()
    await screen.findByText('모두 기계식 키보드')

    search.mockResolvedValue(page([]))
    await userEvent.type(screen.getByLabelText('상품 검색'), '없는상품')
    await userEvent.click(screen.getByRole('button', { name: '검색' }))

    expect(await screen.findByText('검색 결과가 없습니다')).toBeInTheDocument()
  })

  it('links to the create page', async () => {
    vi.spyOn(products, 'searchProducts').mockResolvedValue(page([]))
    renderPage()

    expect(await screen.findByRole('link', { name: '상품 등록' })).toHaveAttribute('href', '/products/new')
  })
})
```

`Layout.test.tsx` 의 `links to the main sections` 에 한 줄 추가:
```tsx
    expect(screen.getByText('상품')).toBeInTheDocument()
```

- [ ] **Step 2: 실패 확인** — `npx vitest run src/pages/ProductsPage.test.tsx src/components/Layout.test.tsx` → import 실패, `상품` 없음

- [ ] **Step 3: 구현**

`src/pages/ProductsPage.tsx`:
```tsx
import { type FormEvent, type KeyboardEvent, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PAGE_SIZE } from '../api/client'
import { searchProducts, type Product } from '../api/products'
import Pager from '../components/Pager'
import { formatPrice } from '../util/format'

export default function ProductsPage() {
  const navigate = useNavigate()
  const [keyword, setKeyword] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [page, setPage] = useState(0)
  const [products, setProducts] = useState<Product[]>([])
  /** 서버가 돌려준 페이지 번호(0-based). 순번은 지금 표에 깔린 데이터 기준으로 매긴다. */
  const [pageNumber, setPageNumber] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    searchProducts(searchTerm, page)
      .then((result) => {
        if (cancelled) return
        setProducts(result.content)
        setPageNumber(result.number)
        setTotalPages(result.totalPages)
      })
      .catch(() => {
        if (!cancelled) setError('상품 목록을 불러오지 못했습니다')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [searchTerm, page])

  const onSearch = (e: FormEvent) => {
    e.preventDefault()
    setPage(0)
    setSearchTerm(keyword.trim())
  }

  const open = (id: number) => navigate(`/products/${id}`)
  const onRowKey = (e: KeyboardEvent, id: number) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      open(id)
    }
  }

  return (
    <div>
      <h1>상품 관리</h1>
      <div className="list-controls">
        <form className="search-form" onSubmit={onSearch}>
          <input
            type="text"
            aria-label="상품 검색"
            placeholder="상품 이름/설명 검색"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <button type="submit" className="btn btn--primary">
            검색
          </button>
        </form>
        <Link to="/products/new" className="btn btn--primary">
          상품 등록
        </Link>
      </div>

      {loading && <p>불러오는 중...</p>}
      {error && <p className="error-text">{error}</p>}
      {!loading && !error && products.length === 0 && <p>{searchTerm ? '검색 결과가 없습니다' : '등록된 상품이 없습니다'}</p>}

      {!loading && !error && products.length > 0 && (
        <>
          <table className="list-table">
            {/* 열 너비를 비율로 못 박는다. 안 그러면 페이지마다 내용 길이를 따라 열이 들썩인다. */}
            <colgroup>
              <col style={{ width: '6%' }} />
              <col style={{ width: '8%' }} />
              <col style={{ width: '30%' }} />
              <col style={{ width: '14%' }} />
              <col style={{ width: '42%' }} />
            </colgroup>
            <thead>
              <tr>
                <th className="num-cell">번호</th>
                <th aria-label="사진" />
                <th>이름</th>
                <th>가격</th>
                <th>설명</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p, i) => (
                <tr key={p.id} className="clickable-row" role="button" tabIndex={0} onClick={() => open(p.id)} onKeyDown={(e) => onRowKey(e, p.id)}>
                  <td className="num-cell">{pageNumber * PAGE_SIZE + i + 1}</td>
                  <td className="avatar-cell">
                    {/* 옆 칸이 이름을 말하므로 사진은 장식이다. */}
                    {p.imageUrl ? <img src={p.imageUrl} alt="" className="product-thumb" /> : <div className="product-thumb image-placeholder" />}
                  </td>
                  <td title={p.name}>{p.name}</td>
                  <td>{formatPrice(p.price)}</td>
                  <td title={p.description}>{p.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}
    </div>
  )
}
```

`App.tsx`: import `ProductsPage` 후 `/rooms/:roomId` 라우트 아래에 `<Route path="/products" element={<ProductsPage />} />`.

`Layout.tsx`: 채팅방 `NavLink` 아래에
```tsx
          <NavLink to="/products" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
            상품
          </NavLink>
```

`styles.css` 끝에:
```css
/* 상품 관리 */
a.btn {
  display: inline-block;
  text-decoration: none;
}

.product-thumb {
  display: block;
  width: 40px;
  height: 40px;
  border-radius: 6px;
  object-fit: cover;
}

.product-preview {
  display: block;
  width: 160px;
  height: 160px;
  border-radius: 8px;
  object-fit: cover;
  background: #eef0f3;
}
```

- [ ] **Step 4: 통과 확인** — `npm test`, 58개 통과
- [ ] **Step 5: 커밋** (modu_messenger) — `Feat: admin 상품 목록 페이지`

### Task 9: admin 상품 등록·수정·삭제 폼 · README

**Files:**
- Create: `admin/src/pages/ProductFormPage.tsx`, `admin/src/pages/ProductFormPage.test.tsx`
- Modify: `admin/src/App.tsx` (`/products/new`, `/products/:id`), `admin/README.md`

**Interfaces:**
- Consumes: Task 7 `getProduct`, `createProduct`, `updateProduct`, `deleteProduct`, `validationMessage`, `ProductInput`; Task 8 CSS `.product-preview`

- [ ] **Step 1: 실패하는 테스트 작성** — `src/pages/ProductFormPage.test.tsx`
```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import * as products from '../api/products'
import ProductFormPage from './ProductFormPage'

const tumbler = { id: 7, name: '모두 텀블러 500ml', description: '하루 종일 차가운', price: 24000, imageUrl: 'https://img/t.png' }

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/products" element={<p>상품 목록 화면</p>} />
        <Route path="/products/new" element={<ProductFormPage />} />
        <Route path="/products/:id" element={<ProductFormPage />} />
      </Routes>
    </MemoryRouter>,
  )

describe('ProductFormPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('creates a product with a numeric price and a null image, then returns to the list', async () => {
    const create = vi.spyOn(products, 'createProduct').mockResolvedValue({ ...tumbler, id: 9 })
    renderAt('/products/new')

    await userEvent.type(screen.getByLabelText('이름'), '모두 우산')
    await userEvent.type(screen.getByLabelText('가격'), '15000')
    await userEvent.type(screen.getByLabelText('설명'), '자동 우산')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    expect(create).toHaveBeenCalledWith({ name: '모두 우산', description: '자동 우산', price: 15000, imageUrl: null })
    expect(await screen.findByText('상품 목록 화면')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
  })

  it('loads the product into the form and saves changes', async () => {
    vi.spyOn(products, 'getProduct').mockResolvedValue(tumbler)
    const update = vi.spyOn(products, 'updateProduct').mockResolvedValue({ ...tumbler, name: '모두 텀블러 750ml' })
    renderAt('/products/7')

    const name = await screen.findByLabelText('이름')
    expect(name).toHaveValue('모두 텀블러 500ml')
    expect(screen.getByLabelText('가격')).toHaveValue(24000)
    expect(screen.getByAltText('미리보기')).toHaveAttribute('src', 'https://img/t.png')

    await userEvent.clear(name)
    await userEvent.type(name, '모두 텀블러 750ml')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(update).toHaveBeenCalledWith('7', { name: '모두 텀블러 750ml', description: '하루 종일 차가운', price: 24000, imageUrl: 'https://img/t.png' })
    expect(await screen.findByText('저장했습니다')).toBeInTheDocument()
  })

  it('shows the server reason when the save is rejected with 400', async () => {
    vi.spyOn(products, 'getProduct').mockResolvedValue(tumbler)
    vi.spyOn(products, 'updateProduct').mockRejectedValue(new ApiError(400, '{"message":"price: 가격은 0 이상이어야 합니다."}'))
    renderAt('/products/7')

    await userEvent.click(await screen.findByRole('button', { name: '저장' }))

    expect(await screen.findByText('price: 가격은 0 이상이어야 합니다.')).toBeInTheDocument()
  })

  it('deletes after confirming and returns to the list', async () => {
    vi.spyOn(products, 'getProduct').mockResolvedValue(tumbler)
    const remove = vi.spyOn(products, 'deleteProduct').mockResolvedValue(undefined)
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderAt('/products/7')

    await userEvent.click(await screen.findByRole('button', { name: '삭제' }))

    expect(confirm).toHaveBeenCalledWith("'모두 텀블러 500ml' 상품을 삭제할까요?")
    expect(remove).toHaveBeenCalledWith('7')
    expect(await screen.findByText('상품 목록 화면')).toBeInTheDocument()
  })

  it('does not delete when the confirmation is cancelled', async () => {
    vi.spyOn(products, 'getProduct').mockResolvedValue(tumbler)
    const remove = vi.spyOn(products, 'deleteProduct').mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderAt('/products/7')

    await userEvent.click(await screen.findByRole('button', { name: '삭제' }))

    expect(remove).not.toHaveBeenCalled()
  })

  it('says the product is gone when it was not found', async () => {
    vi.spyOn(products, 'getProduct').mockRejectedValue(new ApiError(404, '{"message":"없음"}'))
    renderAt('/products/7')

    expect(await screen.findByText('상품을 찾을 수 없습니다')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '← 상품 목록' })).toHaveAttribute('href', '/products')
  })
})
```

- [ ] **Step 2: 실패 확인** — `npx vitest run src/pages/ProductFormPage.test.tsx` → import 실패

- [ ] **Step 3: 구현** — `src/pages/ProductFormPage.tsx`
```tsx
import { type FormEvent, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { createProduct, deleteProduct, getProduct, type ProductInput, updateProduct, validationMessage } from '../api/products'

/** /products/new 는 등록, /products/:id 는 수정·삭제. 입력칸은 같다. */
export default function ProductFormPage() {
  const { id } = useParams<{ id: string }>()
  const editing = id !== undefined
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const [description, setDescription] = useState('')
  const [imageUrl, setImageUrl] = useState('')
  /** 삭제 확인 문구에 쓴다. 입력 중인 이름이 아니라 서버에 저장된 이름이다. */
  const [savedName, setSavedName] = useState('')
  const [previewFailed, setPreviewFailed] = useState(false)

  const [loading, setLoading] = useState(editing)
  const [notFound, setNotFound] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    let cancelled = false
    setLoading(true)
    getProduct(id)
      .then((p) => {
        if (cancelled) return
        setName(p.name)
        setPrice(String(p.price))
        setDescription(p.description)
        setImageUrl(p.imageUrl ?? '')
        setSavedName(p.name)
      })
      .catch((err) => {
        if (cancelled) return
        if (err instanceof ApiError && err.status === 404) setNotFound(true)
        else setLoadError('상품을 불러오지 못했습니다')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [id])

  // 주소를 고치면 이전 주소의 실패 표시는 의미가 없다. 새 주소로 다시 그려 본다.
  useEffect(() => {
    setPreviewFailed(false)
  }, [imageUrl])

  const input = (): ProductInput => ({
    name: name.trim(),
    description: description.trim(),
    price: Number(price),
    imageUrl: imageUrl.trim() === '' ? null : imageUrl.trim(),
  })

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setMessage(null)
    setError(null)
    setSubmitting(true)
    try {
      if (id) {
        const saved = await updateProduct(id, input())
        setSavedName(saved.name)
        setMessage('저장했습니다')
      } else {
        await createProduct(input())
        navigate('/products')
      }
    } catch (err) {
      setError(validationMessage(err) ?? '저장하지 못했습니다')
    } finally {
      setSubmitting(false)
    }
  }

  const onDelete = async () => {
    if (!id || !window.confirm(`'${savedName}' 상품을 삭제할까요?`)) return
    setMessage(null)
    setError(null)
    setSubmitting(true)
    try {
      await deleteProduct(id)
      navigate('/products')
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) setNotFound(true)
      else setError('삭제하지 못했습니다')
      setSubmitting(false)
    }
  }

  const backLink = (
    <Link to="/products" className="back-link">
      ← 상품 목록
    </Link>
  )

  if (loading) return <p>불러오는 중...</p>
  if (notFound)
    return (
      <div>
        {backLink}
        <p>상품을 찾을 수 없습니다</p>
      </div>
    )
  if (loadError) return <p className="error-text">{loadError}</p>

  const preview = imageUrl.trim()

  return (
    <div>
      {backLink}
      <h1>{editing ? '상품 수정' : '상품 등록'}</h1>
      <form className="form-card" onSubmit={onSubmit}>
        <div className="form-section">
          <div className="form-field">
            <label htmlFor="product-name">이름</label>
            <input id="product-name" value={name} maxLength={100} required onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="form-field">
            <label htmlFor="product-price">가격</label>
            <input id="product-price" type="number" min={0} step={1} value={price} required onChange={(e) => setPrice(e.target.value)} />
          </div>
          <div className="form-field">
            <label htmlFor="product-description">설명</label>
            <textarea id="product-description" rows={4} maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>
          <div className="form-field">
            <label htmlFor="product-image">이미지 URL</label>
            <input id="product-image" type="url" placeholder="https://" value={imageUrl} onChange={(e) => setImageUrl(e.target.value)} />
            {preview !== '' &&
              (previewFailed ? (
                <p className="form-hint">이미지를 불러올 수 없습니다</p>
              ) : (
                <img src={preview} alt="미리보기" className="product-preview" onError={() => setPreviewFailed(true)} />
              ))}
          </div>
        </div>
        <div className="form-actions">
          {editing && (
            <button type="button" className="btn btn--danger" onClick={onDelete} disabled={submitting}>
              삭제
            </button>
          )}
          <button type="submit" className="btn btn--primary" disabled={submitting}>
            {editing ? '저장' : '등록'}
          </button>
        </div>
      </form>

      {message && <p className="result-text">{message}</p>}
      {error && <p className="error-text">{error}</p>}
    </div>
  )
}
```

`App.tsx`: import `ProductFormPage` 후 `/products` 라우트 아래에
```tsx
          <Route path="/products/new" element={<ProductFormPage />} />
          <Route path="/products/:id" element={<ProductFormPage />} />
```

`admin/README.md` 기능 목록의 채팅방 상세 줄 아래:
```markdown
- **상품 관리** (`/products`): 모두의 커머스 상품 목록(최신 등록순)·검색·페이지네이션, 행 클릭 시 수정 화면 이동. 게이트웨이 `/commerce-service/api-admin/**` 를 부른다.
- **상품 등록/수정** (`/products/new`, `/products/:id`): 이름·가격·설명·이미지 URL(미리보기). 수정 화면에서 확인 후 삭제(소프트 삭제). 검증 실패 시 서버가 준 이유를 보여 준다.
```

- [ ] **Step 4: 통과 확인** — `npm test` 64개 통과, `npm run build` 성공
- [ ] **Step 5: 커밋** (modu_messenger) — `Feat: admin 상품 등록·수정·삭제 폼`

### Task 10: 앱 API 클라이언트 · 토큰 갱신 헬퍼 · 빈 목록 상태 (JVM 로직)

**Files:**
- Modify: `android/modu_commerce/app/src/main/kotlin/com/example/moducommerce/CommerceApiClient.kt`, `Product.kt`
- Create: `.../TokenRefresher.kt`, `.../EmptyState.kt`
- Test: `app/src/test/kotlin/com/example/moducommerce/CommerceApiClientTest.kt`, `TokenRefresherTest.kt`, `EmptyStateTest.kt`, `ProductTest.kt`

**Interfaces:**
- Produces: `CommerceApiClient.products(accessToken: String, query: String? = null): List<Product>`, `CommerceApiClient.product(accessToken: String, id: Long): Product`, `CommerceApiClient.productsUrl(baseUrl: String, query: String?): HttpUrl`, `Product.parse(json: String): Product`, `TokenRefresher(loadAccess: () -> String?, loadRefresh: () -> String?, renew: (String) -> TokenResponse, save: (TokenResponse) -> Unit)` + `fun <T> withFreshAccess(call: (String) -> T): T` + `TokenRefresher.isSessionExpired(e: Throwable): Boolean`, `sealed class EmptyState { object NoProducts; data class NoResults(val query: String) }` + `EmptyState.of(query: String?)`

- [ ] **Step 1: 실패하는 테스트 작성**

`CommerceApiClientTest.kt`:
```kotlin
package com.example.moducommerce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommerceApiClientTest {

    @Test
    fun `검색어가 없거나 공백이면 q 를 붙이지 않는다`() {
        assertEquals("http://h:8200/api/v1/products", CommerceApiClient.productsUrl("http://h:8200/", null).toString())
        assertEquals("http://h:8200/api/v1/products", CommerceApiClient.productsUrl("http://h:8200", "   ").toString())
    }

    @Test
    fun `한글 검색어는 앞뒤 공백을 떼고 UTF-8 로 인코딩한다`() {
        val url = CommerceApiClient.productsUrl("http://h:8200/", " 키보드 ")

        assertEquals("키보드", url.queryParameter("q"))
        assertTrue(url.toString().endsWith("?q=%ED%82%A4%EB%B3%B4%EB%93%9C"))
    }
}
```

`ProductTest.kt` 에 추가:
```kotlin
    @Test
    fun `단건 JSON 을 파싱한다`() {
        val p = Product.parse("""{"id":7,"name":"모두 우산","description":"자동","price":15000,"imageUrl":"https://img/u.png"}""")
        assertEquals(7L, p.id)
        assertEquals("모두 우산", p.name)
        assertEquals("https://img/u.png", p.imageUrlOrNull())
    }
```

`TokenRefresherTest.kt`:
```kotlin
package com.example.moducommerce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenRefresherTest {

    private var access: String? = "old"
    private var refresh: String? = "rt"
    private val renewCalls = mutableListOf<String>()
    private var saved: TokenResponse? = null
    private var renewResult: () -> TokenResponse = { TokenResponse(accessToken = "new", refreshToken = "rt2") }

    private val refresher = TokenRefresher(
        loadAccess = { access },
        loadRefresh = { refresh },
        renew = { renewCalls += it; renewResult() },
        save = { saved = it },
    )

    private fun unauthorized() = ModuAuthClient.AuthException(401, "expired")

    @Test
    fun `성공하면 갱신하지 않는다`() {
        assertEquals("ok:old", refresher.withFreshAccess { "ok:$it" })
        assertTrue(renewCalls.isEmpty())
    }

    @Test
    fun `401 이면 한 번 갱신하고 새 토큰으로 다시 부른다`() {
        val calls = mutableListOf<String>()

        val result = refresher.withFreshAccess { token ->
            calls += token
            if (token == "old") throw unauthorized()
            "ok:$token"
        }

        assertEquals("ok:new", result)
        assertEquals(listOf("old", "new"), calls)
        assertEquals(listOf("rt"), renewCalls)
        assertEquals("new", saved?.accessToken)
    }

    @Test
    fun `refresh 토큰이 없으면 갱신하지 않고 401 로 실패한다`() {
        refresh = null

        val e = assertThrows(ModuAuthClient.AuthException::class.java) { refresher.withFreshAccess<String> { throw unauthorized() } }

        assertEquals(401, e.status)
        assertTrue(renewCalls.isEmpty())
    }

    @Test
    fun `401 이 아닌 오류는 갱신하지 않고 그대로 던진다`() {
        val e = assertThrows(ModuAuthClient.AuthException::class.java) {
            refresher.withFreshAccess<String> { throw ModuAuthClient.AuthException(500, "boom") }
        }

        assertEquals(500, e.status)
        assertTrue(renewCalls.isEmpty())
    }

    @Test
    fun `다시 불러도 401 이면 더 반복하지 않는다`() {
        var calls = 0

        val e = assertThrows(ModuAuthClient.AuthException::class.java) { refresher.withFreshAccess<String> { calls++; throw unauthorized() } }

        assertEquals(401, e.status)
        assertEquals(2, calls)
        assertEquals(1, renewCalls.size)
    }

    @Test
    fun `갱신이 실패하면 세션 만료(401)로 알린다`() {
        renewResult = { throw ModuAuthClient.AuthException(400, "invalid_grant") }

        val e = assertThrows(ModuAuthClient.AuthException::class.java) { refresher.withFreshAccess<String> { throw unauthorized() } }

        assertTrue(TokenRefresher.isSessionExpired(e))
    }

    @Test
    fun `저장된 토큰이 없으면 부르지 않고 401`() {
        access = null
        var called = false

        val e = assertThrows(ModuAuthClient.AuthException::class.java) { refresher.withFreshAccess { called = true } }

        assertEquals(401, e.status)
        assertEquals(false, called)
    }
}
```

`EmptyStateTest.kt`:
```kotlin
package com.example.moducommerce

import org.junit.Assert.assertEquals
import org.junit.Test

class EmptyStateTest {

    @Test
    fun `검색어가 없으면 상품이 없는 상태`() {
        assertEquals(EmptyState.NoProducts, EmptyState.of(null))
        assertEquals(EmptyState.NoProducts, EmptyState.of("  "))
    }

    @Test
    fun `검색어가 있으면 검색 결과가 없는 상태`() {
        assertEquals(EmptyState.NoResults("키보드"), EmptyState.of(" 키보드 "))
    }
}
```

- [ ] **Step 2: 실패 확인** — 안드로이드 테스트 명령 → 컴파일 실패 (`productsUrl`, `TokenRefresher`, `EmptyState`, `Product.parse` 없음)

- [ ] **Step 3: 구현**

`CommerceApiClient.kt` 전체:
```kotlin
package com.example.moducommerce

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** commerce-service 호출. auth-service 가 발급한 modu-commerce 액세스 토큰을 Bearer 로 보낸다. */
class CommerceApiClient(baseUrl: String) {

    private val baseUrl = withSlash(baseUrl)
    private val http = OkHttpClient()

    fun products(accessToken: String, query: String? = null): List<Product> =
        Product.parseList(get(productsUrl(baseUrl, query), accessToken))

    /** 삭제됐거나 없는 상품이면 AuthException(404). */
    fun product(accessToken: String, id: Long): Product =
        Product.parse(get((baseUrl + "api/v1/products/$id").toHttpUrl(), accessToken))

    private fun get(url: HttpUrl, accessToken: String): String {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw ModuAuthClient.AuthException(res.code, body)
            return body
        }
    }

    companion object {
        private fun withSlash(url: String) = if (url.endsWith("/")) url else "$url/"

        /** 검색어가 비면 q 를 붙이지 않는다. 한글은 OkHttp 가 UTF-8 로 인코딩한다. */
        fun productsUrl(baseUrl: String, query: String?): HttpUrl {
            val builder = (withSlash(baseUrl) + "api/v1/products").toHttpUrl().newBuilder()
            query?.trim()?.takeIf { it.isNotEmpty() }?.let { builder.addQueryParameter("q", it) }
            return builder.build()
        }
    }
}
```

`Product.kt` companion 에 추가:
```kotlin
        fun parse(json: String): Product = Gson().fromJson(json, Product::class.java)
```

`TokenRefresher.kt`:
```kotlin
package com.example.moducommerce

/**
 * 액세스 토큰(TTL 1시간)이 만료돼 401 이 오면 refresh 토큰으로 한 번만 갱신하고 다시 부른다.
 * 갱신할 수 없으면 AuthException(401) 로 알린다 — 화면은 이걸 세션 만료로 보고 로그인으로 보낸다.
 * 토큰 저장소와 갱신 호출은 주입받아 JVM 테스트에서 가짜로 바꿀 수 있게 한다. 백그라운드 스레드에서 쓴다.
 */
class TokenRefresher(
    private val loadAccess: () -> String?,
    private val loadRefresh: () -> String?,
    private val renew: (String) -> TokenResponse,
    private val save: (TokenResponse) -> Unit,
) {

    fun <T> withFreshAccess(call: (String) -> T): T {
        val access = loadAccess() ?: throw sessionExpired("저장된 액세스 토큰이 없습니다")
        try {
            return call(access)
        } catch (e: ModuAuthClient.AuthException) {
            if (e.status != 401) throw e
        }
        val refreshToken = loadRefresh() ?: throw sessionExpired("refresh 토큰이 없습니다")
        val renewed = try {
            renew(refreshToken)
        } catch (e: Exception) {
            throw sessionExpired("토큰 갱신 실패: ${e.message}")
        }
        val renewedAccess = renewed.accessToken ?: throw sessionExpired("갱신 응답에 액세스 토큰이 없습니다")
        save(renewed)
        return call(renewedAccess)
    }

    companion object {
        fun isSessionExpired(e: Throwable): Boolean = e is ModuAuthClient.AuthException && e.status == 401

        private fun sessionExpired(reason: String) = ModuAuthClient.AuthException(401, reason)
    }
}
```

`EmptyState.kt`:
```kotlin
package com.example.moducommerce

/** 상품 목록이 비었을 때 보여 줄 안내의 종류. 문구는 화면이 strings.xml 에서 고른다. */
sealed class EmptyState {
    object NoProducts : EmptyState()
    data class NoResults(val query: String) : EmptyState()

    companion object {
        fun of(query: String?): EmptyState =
            query?.trim()?.takeIf { it.isNotEmpty() }?.let { NoResults(it) } ?: NoProducts
    }
}
```

- [ ] **Step 4: 통과 확인** — 안드로이드 테스트 명령, 23개 통과 (11 + 12)
- [ ] **Step 5: 커밋** — `Feat: 앱 상품 검색 URL·단건 조회·토큰 갱신 헬퍼`

### Task 11: 앱 목록 검색 · 카드 클릭 · 토큰 갱신 적용

**Files:**
- Modify: `app/src/main/kotlin/com/example/moducommerce/ProductListActivity.kt`, `ProductAdapter.kt`
- Modify: `app/src/main/res/menu/menu_products.xml`, `res/layout/product_row.xml`, `res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_search.xml`

**Interfaces:**
- Consumes: Task 10 `TokenRefresher`, `EmptyState`, `CommerceApiClient.products(access, query)`
- Produces: `ProductAdapter(onClick: (Product) -> Unit)`; 목록이 `ProductDetailActivity.EXTRA_PRODUCT_ID`(Task 12)로 상세를 연다

화면 코드는 JVM 테스트 대상이 아니다(로직은 Task 10 에서 테스트). 이 태스크의 검증은 빌드와 기존·신규 단위 테스트 통과, Task 13 의 기기 확인이다. Task 12 와 함께 빌드되므로 **Task 12 까지 끝낸 뒤 한 번에 빌드·커밋한다.**

- [ ] **Step 1: 리소스**

`res/drawable/ic_search.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 앱바 검색 아이콘. 머티리얼 search 24dp. 앱바가 붉은 바탕이라 흰색으로 칠한다. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/white"
        android:pathData="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z" />
</vector>
```

`res/menu/menu_products.xml` 의 로그아웃 항목 **앞에**:
```xml
    <!-- 검색 버튼(submit)을 누를 때만 서버를 부른다. 닫으면 전체 목록으로 돌아간다. -->
    <item
        android:id="@+id/action_search"
        android:icon="@drawable/ic_search"
        android:title="@string/action_search"
        app:actionViewClass="androidx.appcompat.widget.SearchView"
        app:showAsAction="always|collapseActionView" />
```

`res/layout/product_row.xml` 의 `MaterialCardView` 속성에 추가 (눌림 효과):
```xml
    android:clickable="true"
    android:focusable="true"
```

`res/values/strings.xml` 상품 목록 절에 추가:
```xml
    <string name="action_search">검색</string>
    <string name="products_search_hint">상품 이름·설명 검색</string>
    <string name="products_no_results">\'%1$s\' 검색 결과가 없습니다</string>
```

- [ ] **Step 2: `ProductAdapter.kt` — 클릭 콜백**

클래스 선언과 `onCreateViewHolder`, `Holder` 를 바꾼다:
```kotlin
/** 상품 카드 목록. 이미지 주소가 없는 상품은 플레이스홀더(bg_product_thumbnail)가 그대로 보인다. 카드를 누르면 onClick. */
class ProductAdapter(private val onClick: (Product) -> Unit) : RecyclerView.Adapter<ProductAdapter.Holder>() {
```
```kotlin
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.product_row, parent, false), onClick)
```
```kotlin
    class Holder(view: View, private val onClick: (Product) -> Unit) : RecyclerView.ViewHolder(view) {
```
`bind` 첫 줄에:
```kotlin
            itemView.setOnClickListener { onClick(product) }
```

- [ ] **Step 3: `ProductListActivity.kt` 전체 교체**
```kotlin
package com.example.moducommerce

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.gson.JsonParser
import java.util.concurrent.Executors

/**
 * 로그인 이후의 첫 화면. 로그인한 계정을 한 줄 보여 주고 commerce-service 의 상품 목록을 띄운다.
 * 앱바에서 검색하고, 카드를 누르면 상세로 간다. 모든 호출은 TokenRefresher 를 거쳐 401 이면 한 번 갱신하고,
 * 그래도 안 되면 토큰을 비우고 로그인 화면으로 돌려보낸다.
 */
class ProductListActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var account: TextView
    private lateinit var status: TextView
    private lateinit var tokenStore: TokenStore
    private lateinit var auth: ModuAuthClient
    private lateinit var refresher: TokenRefresher
    private lateinit var commerce: CommerceApiClient
    private lateinit var googleClient: GoogleSignInClient
    private val adapter = ProductAdapter { openDetail(it) }

    /** 지금 목록이 보여 주는 검색어. null 이면 전체. 상세에서 돌아와 다시 불러올 때도 쓴다. */
    private var currentQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_products)
        setTitle(R.string.app_name)

        account = findViewById(R.id.products_account)
        status = findViewById(R.id.products_status)
        findViewById<RecyclerView>(R.id.products).let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = adapter
        }

        tokenStore = TokenStore(this)
        auth = ModuAuthClient(BuildConfig.API_BASE_URL)
        refresher = TokenRefresher(
            loadAccess = { tokenStore.access() },
            loadRefresh = { tokenStore.refresh() },
            renew = { auth.refresh(it) },
            save = { tokenStore.save(it) },
        )
        commerce = CommerceApiClient(BuildConfig.COMMERCE_API_URL)
        googleClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )

        if (tokenStore.access() == null) backToLogin(sessionExpired = true) else showMe()
    }

    /** 상세에서 돌아오면 그 사이 백오피스에서 바뀐 내용을 반영하도록 지금 검색어로 다시 불러온다. */
    override fun onRestart() {
        super.onRestart()
        if (tokenStore.access() != null) loadProducts()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_products, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.products_search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                search(query)
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean = false
        })
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                if (currentQuery != null) search(null)
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_logout -> {
            logout()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** /userinfo 로 이름을 받아 보여준 뒤 상품을 불러온다. */
    private fun showMe() {
        showStatus(getString(R.string.products_loading))
        io.execute {
            try {
                val body = refresher.withFreshAccess { auth.userinfo(it) }
                val me = JsonParser.parseString(body).asJsonObject
                val name = me.get("name")?.asString ?: ""
                val email = me.get("email")?.asString ?: ""
                main.post {
                    account.text = getString(R.string.products_signed_in_as, name, email)
                    loadProducts()
                }
            } catch (e: Exception) {
                Log.e(TAG, "userinfo failed", e)
                main.post { onFailure(e) }
            }
        }
    }

    private fun search(query: String?) {
        currentQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        showStatus(getString(R.string.products_loading))
        loadProducts()
    }

    /** commerce-service 에서 상품 목록을 받아 보여준다. 같은 모두 계정 토큰으로 부른다. */
    private fun loadProducts() {
        val query = currentQuery
        io.execute {
            try {
                val list = refresher.withFreshAccess { commerce.products(it, query) }
                // 응답이 오는 사이 검색어가 바뀌었으면 옛 결과로 덮지 않는다.
                main.post { if (query == currentQuery) showProducts(list, query) }
            } catch (e: Exception) {
                Log.e(TAG, "products failed", e)
                main.post { onFailure(e) }
            }
        }
    }

    private fun showProducts(list: List<Product>, query: String?) {
        adapter.submit(list)
        if (list.isNotEmpty()) {
            hideStatus()
            return
        }
        showStatus(
            when (val empty = EmptyState.of(query)) {
                EmptyState.NoProducts -> getString(R.string.products_empty)
                is EmptyState.NoResults -> getString(R.string.products_no_results, empty.query)
            }
        )
    }

    private fun onFailure(e: Exception) {
        if (TokenRefresher.isSessionExpired(e)) {
            tokenStore.clear()
            backToLogin(sessionExpired = true)
        } else {
            showStatus(getString(R.string.products_failed, e.message))
        }
    }

    private fun openDetail(product: Product) {
        startActivity(Intent(this, ProductDetailActivity::class.java).putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.id))
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        status.visibility = View.GONE
    }

    private fun logout() {
        val refresh = tokenStore.refresh()
        tokenStore.clear()
        googleClient.signOut()
        backToLogin(sessionExpired = false)
        if (refresh == null) return
        io.execute {
            try {
                auth.revoke(refresh)
            } catch (e: Exception) {
                Log.w(TAG, "revoke failed: ${e.message}")
            }
        }
    }

    private fun backToLogin(sessionExpired: Boolean) {
        val intent = Intent(this, LoginActivity::class.java)
            .putExtra(LoginActivity.EXTRA_SESSION_EXPIRED, sessionExpired)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "ModuCommerce"
    }
}
```

### Task 12: 앱 상품 상세 화면 · README

**Files:**
- Create: `app/src/main/kotlin/com/example/moducommerce/ProductDetailActivity.kt`
- Create: `app/src/main/res/layout/activity_product_detail.xml`, `res/drawable/bg_product_detail_image.xml`
- Modify: `app/src/main/AndroidManifest.xml`, `res/values/strings.xml`, `README.md`

**Interfaces:**
- Consumes: Task 10 `TokenRefresher`, `CommerceApiClient.product(access, id)`, 기존 `Product.imageUrlOrNull()`, `LoginActivity.EXTRA_SESSION_EXPIRED`
- Produces: `ProductDetailActivity.EXTRA_PRODUCT_ID = "product_id"` (Long)

- [ ] **Step 1: 리소스**

`res/drawable/bg_product_detail_image.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 상세 화면 사진 자리. 사진이 없거나 불러오는 동안 로고를 크게 깐다. -->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@color/product_surface" />
    <item
        android:width="96dp"
        android:height="92dp"
        android:drawable="@drawable/modu_logo"
        android:gravity="center" />
</layer-list>
```

`res/layout/activity_product_detail.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 상품 상세. 전체 폭 정사각형 사진 아래에 이름·가격·설명을 차례로 둔다. 불러오는 동안·실패 시에는 가운데 안내만 보인다. -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/white">

    <androidx.core.widget.NestedScrollView
        android:id="@+id/detail_content"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone">

        <androidx.constraintlayout.widget.ConstraintLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingBottom="32dp">

            <ImageView
                android:id="@+id/detail_image"
                android:layout_width="0dp"
                android:layout_height="0dp"
                android:background="@drawable/bg_product_detail_image"
                android:contentDescription="@string/products_thumbnail"
                android:scaleType="centerCrop"
                app:layout_constraintDimensionRatio="1:1"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintTop_toTopOf="parent" />

            <TextView
                android:id="@+id/detail_name"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="20dp"
                android:layout_marginTop="20dp"
                android:layout_marginEnd="20dp"
                android:textColor="@color/text_primary"
                android:textSize="22sp"
                android:textStyle="bold"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintTop_toBottomOf="@id/detail_image" />

            <TextView
                android:id="@+id/detail_price"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:textColor="@color/brand_red"
                android:textSize="20sp"
                android:textStyle="bold"
                app:layout_constraintEnd_toEndOf="@id/detail_name"
                app:layout_constraintStart_toStartOf="@id/detail_name"
                app:layout_constraintTop_toBottomOf="@id/detail_name" />

            <View
                android:id="@+id/detail_divider"
                android:layout_width="0dp"
                android:layout_height="1dp"
                android:layout_marginTop="18dp"
                android:background="@color/divider"
                app:layout_constraintEnd_toEndOf="@id/detail_name"
                app:layout_constraintStart_toStartOf="@id/detail_name"
                app:layout_constraintTop_toBottomOf="@id/detail_price" />

            <TextView
                android:id="@+id/detail_description"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginTop="18dp"
                android:lineSpacingExtra="4dp"
                android:textColor="@color/text_primary"
                android:textSize="15sp"
                app:layout_constraintEnd_toEndOf="@id/detail_name"
                app:layout_constraintStart_toStartOf="@id/detail_name"
                app:layout_constraintTop_toBottomOf="@id/detail_divider" />
        </androidx.constraintlayout.widget.ConstraintLayout>
    </androidx.core.widget.NestedScrollView>

    <TextView
        android:id="@+id/detail_status"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:gravity="center"
        android:padding="24dp"
        android:textColor="@color/grey"
        android:textSize="15sp" />
</FrameLayout>
```

`strings.xml` 에 상세 절 추가:
```xml
    <!-- 상품 상세 화면 -->
    <string name="detail_loading">상품을 불러오는 중…</string>
    <string name="detail_not_found">삭제됐거나 없는 상품입니다</string>
    <string name="detail_failed">상품을 불러오지 못했습니다</string>
```

`AndroidManifest.xml` 의 `ProductListActivity` 아래:
```xml
        <activity
            android:name=".ProductDetailActivity"
            android:exported="false"
            android:parentActivityName=".ProductListActivity" />
```

- [ ] **Step 2: `ProductDetailActivity.kt`**
```kotlin
package com.example.moducommerce

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import java.util.concurrent.Executors

/**
 * 상품 상세. 목록에서 id 만 받아 서버에서 최신 값을 다시 읽는다 —
 * 목록을 받은 뒤 백오피스에서 수정·삭제됐을 수 있다.
 */
class ProductDetailActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var content: View
    private lateinit var status: TextView
    private lateinit var tokenStore: TokenStore
    private lateinit var refresher: TokenRefresher
    private lateinit var commerce: CommerceApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_detail)
        setTitle(R.string.app_name)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        content = findViewById(R.id.detail_content)
        status = findViewById(R.id.detail_status)
        tokenStore = TokenStore(this)
        val auth = ModuAuthClient(BuildConfig.API_BASE_URL)
        refresher = TokenRefresher(
            loadAccess = { tokenStore.access() },
            loadRefresh = { tokenStore.refresh() },
            renew = { auth.refresh(it) },
            save = { tokenStore.save(it) },
        )
        commerce = CommerceApiClient(BuildConfig.COMMERCE_API_URL)

        val id = intent.getLongExtra(EXTRA_PRODUCT_ID, -1L)
        if (id < 0) {
            finish()
            return
        }
        load(id)
    }

    /** 앱바 뒤로가기. 목록은 스택에 그대로 있으니 닫기만 한다. */
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load(id: Long) {
        showStatus(getString(R.string.detail_loading))
        io.execute {
            try {
                val product = refresher.withFreshAccess { commerce.product(it, id) }
                main.post { show(product) }
            } catch (e: Exception) {
                Log.e(TAG, "product $id failed", e)
                main.post {
                    when {
                        TokenRefresher.isSessionExpired(e) -> expireSession()
                        e is ModuAuthClient.AuthException && e.status == 404 -> showStatus(getString(R.string.detail_not_found))
                        else -> showStatus(getString(R.string.detail_failed))
                    }
                }
            }
        }
    }

    private fun show(product: Product) {
        findViewById<TextView>(R.id.detail_name).text = product.name ?: ""
        findViewById<TextView>(R.id.detail_price).text = product.priceLabel()
        findViewById<TextView>(R.id.detail_description).apply {
            text = product.description ?: ""
            visibility = if (product.description.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val image = findViewById<ImageView>(R.id.detail_image)
        product.imageUrlOrNull()?.let { Glide.with(image).load(it).centerCrop().into(image) }

        status.visibility = View.GONE
        content.visibility = View.VISIBLE
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
        content.visibility = View.GONE
    }

    /** 목록도 같은 토큰을 쓰므로 스택을 통째로 비우고 로그인으로 간다. */
    private fun expireSession() {
        tokenStore.clear()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra(LoginActivity.EXTRA_SESSION_EXPIRED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "product_id"
        private const val TAG = "ModuCommerce"
    }
}
```

`README.md` 앱 화면 흐름 문단 끝에 추가:
```markdown
상품 목록 앱바의 돋보기로 이름·설명을 검색하고(검색 버튼을 누를 때만 조회), 카드를 누르면 **상품 상세**(`ProductDetailActivity`)로 가서 서버에서 최신 값을 다시 읽습니다. 액세스 토큰이 만료되면 `TokenRefresher` 가 refresh 토큰으로 한 번 갱신하고, 갱신도 실패하면 로그인 화면으로 돌아갑니다.
```

- [ ] **Step 3: 빌드·테스트** — 안드로이드 테스트 명령, BUILD SUCCESSFUL, 23개 통과, `app-debug.apk` 생성
- [ ] **Step 4: 커밋** (Task 11 + 12) — `Feat: 앱 상품 검색과 상세 화면`

### Task 13: 배포 · 실기기 검증 (코드 변경 없음)

- [ ] **Step 1: commerce-service 재배포**
```bash
cd /Users/imjunseob/workspace/modu_commerce/backend/commerce-service && ./gradlew bootJar
cd .. && docker compose up -d --build commerce-service
docker logs --since 2m commerce-service 2>&1 | grep -E "Started|ERROR" | head
docker exec mysql-commerce sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SHOW COLUMNS FROM commerce.products LIKE \"deleted_at\""'
```
Expected: `Started CommerceApiApplicationKt`, `deleted_at datetime(6) YES`
- [ ] **Step 2: gateway 재배포**
```bash
cd /Users/imjunseob/workspace/modu_messenger/backend/gateway-service && ./gradlew clean build -x test
cd .. && docker compose up -d --force-recreate --build gateway-service
```
- [ ] **Step 3: 경로·인증 확인 (토큰 없이)**
```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8000/commerce-service/api-admin/v1/products   # 401 (게이트웨이)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8200/api-admin/v1/products                   # 401 (commerce 직접)
```
- [ ] **Step 4: admin** — `localhost:5173` 개발 서버가 떠 있으면 HMR 로 반영된다. 사용자가 로그인해 상품 등록·수정·삭제를 해 본다.
- [ ] **Step 5: 앱 설치**
```bash
adb -s R5CR82KBEWB install -r android/modu_commerce/app/build/outputs/apk/debug/app-debug.apk
```
- [ ] **Step 6: 사용자 확인 목록** — admin 등록 → 앱 목록(돌아올 때 새로고침)·검색·상세 반영 / admin 수정 → 상세 반영 / admin 삭제 → 목록에서 사라지고, 열려 있던 상세는 "삭제됐거나 없는 상품입니다"

