package com.example.commerce.api.coupon

import com.example.commerce.api.common.CustomerRequired
import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.common.userId
import com.example.commerce.application.domain.entity.CouponScope
import com.example.commerce.application.domain.entity.DiscountType
import com.example.commerce.application.domain.entity.UserCouponStatus
import com.example.commerce.application.usecase.command.CouponCommand
import com.example.commerce.application.usecase.command.OrderLineCommand
import com.example.commerce.application.usecase.coupon.AdminCouponUseCase
import com.example.commerce.application.usecase.coupon.ApplicableCouponsUseCase
import com.example.commerce.application.usecase.coupon.IssueCouponUseCase
import com.example.commerce.application.usecase.coupon.MyCouponsUseCase
import com.example.commerce.application.usecase.result.AdminCouponDetailResult
import com.example.commerce.application.usecase.result.AdminCouponIssueResult
import com.example.commerce.application.usecase.result.AdminCouponSummaryResult
import com.example.commerce.application.usecase.result.CouponClaimResult
import com.example.commerce.application.usecase.result.CouponGrantResult
import com.example.commerce.application.usecase.result.CouponOfferResult
import com.example.commerce.application.usecase.result.MyCouponResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** 앱: 쿠폰함, 쿠폰존·상품 쿠폰 받기, 코드 등록, 결제 화면 쿠폰 계산. */
@Tag(
    name = "쿠폰 (앱)",
    description =
        "커머스 웹/앱(웹뷰)이 같은 출처 /api 로 부른다(nginx → commerce-service). 모두 계정 토큰(aud modu-commerce) 필요. " +
            "받을 수 있는 쿠폰 목록은 커머스 가입 전에도 보고, 나머지는 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
)
@RestController
@RequestMapping("/api/v1")
class CouponController(
    private val myCouponsUseCase: MyCouponsUseCase,
    private val issueCouponUseCase: IssueCouponUseCase,
    private val applicableCouponsUseCase: ApplicableCouponsUseCase,
) {
    @Operation(
        summary = "쿠폰함 조회",
        description =
            "내 쿠폰 중 상태가 맞는 것을 돌려준다. AVAILABLE 은 만료 임박 순, USED·EXPIRED 는 최근 받은 순. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @GetMapping("/me/coupons")
    fun myCoupons(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "상태: AVAILABLE(쓸 수 있음, 기본), USED(사용함), EXPIRED(기한 지남)", example = "AVAILABLE")
        @RequestParam(defaultValue = "AVAILABLE") status: UserCouponStatus,
    ): ResponseEntity<List<MyCouponResult>> = ResponseEntity.ok(myCouponsUseCase.list(jwt.userId(), status))

    @Operation(
        summary = "쓸 수 있는 쿠폰 수 조회",
        description = "마이 탭 배지용. {available: 개수} 를 돌려준다. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @GetMapping("/me/coupons/count")
    fun count(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Map<String, Int>> = ResponseEntity.ok(mapOf("available" to myCouponsUseCase.availableCount(jwt.userId())))

    @Operation(
        summary = "받을 수 있는 쿠폰 조회",
        description =
            "쿠폰존·상품 상세의 쿠폰 받기 목록. 받기 노출이 켜져 있고 발급 기간 안인 쿠폰을 돌려주고, 쿠폰마다 이미 받았는지(downloaded)를 붙인다. " +
                "productId 를 주면 그 상품에 쓸 수 있는 쿠폰만(없는 상품이면 빈 목록).",
    )
    @GetMapping("/coupons/downloadable")
    fun downloadable(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "상품 id. 주면 그 상품에 적용되는 쿠폰만", example = "1")
        @RequestParam(required = false) productId: Long?,
    ): ResponseEntity<List<CouponOfferResult>> = ResponseEntity.ok(myCouponsUseCase.downloadable(jwt.userId(), productId))

    @Operation(
        summary = "쿠폰 받기",
        description =
            "받기 노출 쿠폰을 쿠폰함에 넣는다(한 사람 한 장, 수량은 잠근 채 센다). 없는 쿠폰이면 404, 이미 받았으면 409, " +
                "꺼졌거나 기간 밖이거나 소진됐으면 400. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @PostMapping("/coupons/{couponId}/download")
    fun download(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "쿠폰 id", example = "2")
        @PathVariable couponId: Long,
    ): ResponseEntity<MyCouponResult> = ResponseEntity.ok(issueCouponUseCase.download(jwt.userId(), couponId))

    @Operation(
        summary = "쿠폰 코드 등록",
        description =
            "쿠폰 코드(대소문자·앞뒤 공백 무시)로 쿠폰을 받는다. 코드가 비었거나 기간 밖·소진이면 400, 없는 코드거나 꺼진 쿠폰이면 404, " +
                "이미 받았으면 409. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @PostMapping("/coupons/redeem")
    fun redeem(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: RedeemRequest,
    ): ResponseEntity<MyCouponResult> {
        val code = requireNotNull(request.code?.takeIf { it.isNotBlank() }) { "쿠폰 코드를 입력하세요." }
        return ResponseEntity.ok(issueCouponUseCase.redeem(jwt.userId(), code))
    }

    @Operation(
        summary = "결제 쿠폰 계산",
        description =
            "결제 화면용. 주문할 옵션·수량으로 내 쓸 수 있는 쿠폰마다 할인액(discount)과 쓸 수 있는지(applicable), 못 쓰는 이유(reason)를 계산한다. " +
                "쓸 수 있는 쿠폰이 먼저, 그 안에서는 할인액 큰 순. 상품이 없으면 400. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @PostMapping("/coupons/applicable")
    fun applicable(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: ApplicableRequest,
    ): ResponseEntity<List<MyCouponResult>> =
        ResponseEntity.ok(
            applicableCouponsUseCase.execute(
                jwt.userId(),
                request.items.orEmpty().map { OrderLineCommand(requireNotNull(it.skuId), requireNotNull(it.quantity)) },
            ),
        )

    @Operation(
        summary = "쿠폰 이벤트 쿠폰 받기",
        description =
            "진행 중인 쿠폰 이벤트의 쿠폰 중 아직 없는 것을 모두 받는다(쿠폰마다 따로 발급해 한 장이 소진돼도 나머지는 받는다). " +
                "받은 쿠폰과 이미 갖고 있던 수를 돌려준다. 없거나 숨긴 이벤트면 404, 전부 이미 받았으면 409, " +
                "쿠폰 이벤트가 아니거나 진행 중이 아니거나 하나도 못 받으면 400. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @PostMapping("/promotions/{id}/coupons")
    fun claimEvent(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "이벤트(기획전) id", example = "4")
        @PathVariable id: Long,
    ): ResponseEntity<CouponClaimResult> = ResponseEntity.ok(issueCouponUseCase.claimEvent(jwt.userId(), id))
}

@Schema(description = "쿠폰 코드 등록 요청")
data class RedeemRequest(
    @field:Schema(description = "쿠폰 코드. 필수, 대소문자·앞뒤 공백은 무시한다", example = "WELCOME10")
    val code: String? = null,
)

@Schema(description = "쿠폰 계산에 쓸 주문 한 줄")
data class ApplicableLine(
    @field:Schema(description = "옵션(SKU) id. 필수", example = "12")
    val skuId: Long? = null,
    @field:Schema(description = "수량. 필수. 같은 옵션이 여러 줄이면 합친다", example = "1")
    val quantity: Int? = null,
)

@Schema(description = "결제 쿠폰 계산 요청")
data class ApplicableRequest(
    @field:Schema(description = "주문할 옵션·수량 목록. 필수, 한 줄 이상")
    val items: List<ApplicableLine>? = null,
)

/** 백오피스: 쿠폰 관리, 발급 현황, 회원 지급. */
@Tag(
    name = "쿠폰 관리 (어드민)",
    description = "어드민 콘솔용. 게이트웨이 /commerce-service/api-admin/** 가 직원 토큰(ROLE_ADMIN, aud modu-admin)을 확인해 넘긴다.",
)
@RestController
@RequestMapping("/api-admin/v1/coupons")
class AdminCouponController(
    private val adminCouponUseCase: AdminCouponUseCase,
) {
    @Operation(
        summary = "쿠폰 목록 검색",
        description = "지우지 않은 쿠폰을 최신 순(id 내림차순)으로 한 페이지 돌려준다. 쿠폰마다 적용 범위 문구와 발급·사용 수가 붙는다.",
    )
    @GetMapping
    fun coupons(
        @Parameter(description = "쿠폰 이름 또는 코드 부분 일치(대소문자 무시)", example = "WELCOME")
        @RequestParam(required = false) q: String?,
        @Parameter(description = "true 면 사용 중(active)인 쿠폰만, false 면 꺼 둔 쿠폰만. 없으면 전체", example = "true")
        @RequestParam(required = false) active: Boolean?,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 15, 1~100 으로 자른다", example = "15")
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<PageResponse<AdminCouponSummaryResult>> =
        ResponseEntity.ok(PageResponse.from(adminCouponUseCase.search(q, active, page, size)) { it })

    @Operation(
        summary = "쿠폰 상세 조회",
        description = "설명과 적용 대상(카테고리·상품 id 와 이름)까지 돌려준다. 없거나 지운 쿠폰이면 404.",
    )
    @GetMapping("/{id}")
    fun coupon(
        @Parameter(description = "쿠폰 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<AdminCouponDetailResult> = ResponseEntity.ok(adminCouponUseCase.detail(id))

    @Operation(
        summary = "쿠폰 만들기",
        description =
            "쿠폰을 만들고 201 과 상세를 돌려준다. 할인·기간·수량·코드 규칙을 어기거나 코드가 이미 쓰이고 있으면(지운 쿠폰 포함) " +
                "400 과 안내 문구를 돌려준다.",
    )
    @PostMapping
    fun create(
        @RequestBody request: CouponRequest,
    ): ResponseEntity<AdminCouponDetailResult> =
        ResponseEntity.status(HttpStatus.CREATED).body(adminCouponUseCase.create(request.toCommand()))

    @Operation(
        summary = "쿠폰 수정",
        description = "모든 값을 다시 보내 통째로 바꾼다. 이미 받은 쿠폰의 사용 기한은 바뀌지 않는다. 없는 쿠폰이면 404, 규칙 위반이면 400.",
    )
    @PutMapping("/{id}")
    fun update(
        @Parameter(description = "쿠폰 id", example = "1")
        @PathVariable id: Long,
        @RequestBody request: CouponRequest,
    ): ResponseEntity<AdminCouponDetailResult> = ResponseEntity.ok(adminCouponUseCase.update(id, request.toCommand()))

    @Operation(
        summary = "쿠폰 삭제",
        description = "소프트 삭제하고 204 를 돌려준다. 더는 발급되지 않지만 이미 받은 쿠폰은 기한까지 쓸 수 있다. 없는 쿠폰이면 404.",
    )
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(description = "쿠폰 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        adminCouponUseCase.delete(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "쿠폰 발급 현황 조회",
        description = "이 쿠폰을 받은 회원을 최신 순으로 한 페이지 돌려준다(받은 경로·사용 여부·기한). 없는 쿠폰이면 404.",
    )
    @GetMapping("/{id}/issues")
    fun issues(
        @Parameter(description = "쿠폰 id", example = "1")
        @PathVariable id: Long,
        @Parameter(
            description = "AVAILABLE(쓸 수 있음), USED(사용함), EXPIRED(안 쓰고 기한 지남, 오늘 KST 기준). 없으면 전체",
            example = "AVAILABLE",
        )
        @RequestParam(required = false) status: UserCouponStatus?,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 15, 1~100 으로 자른다", example = "15")
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<PageResponse<AdminCouponIssueResult>> =
        ResponseEntity.ok(PageResponse.from(adminCouponUseCase.issues(id, status, page, size)) { it })

    @Operation(
        summary = "회원에게 쿠폰 지급",
        description =
            "고른 회원(1~100명)에게 한 장씩 발급한다. 꺼 둔 쿠폰이나 발급 기간 밖이어도 줄 수 있고, 사람마다 따로 처리해 " +
                "이미 받았거나 소진된 사람은 건너뛰고 이유를 돌려준다. 인원이 범위를 벗어나면 400, 없는 쿠폰이면 404.",
    )
    @PostMapping("/{id}/issues")
    fun grant(
        @Parameter(description = "쿠폰 id", example = "1")
        @PathVariable id: Long,
        @RequestBody request: GrantRequest,
    ): ResponseEntity<CouponGrantResult> = ResponseEntity.ok(adminCouponUseCase.grant(id, request.userIds.orEmpty()))
}

@Schema(description = "쿠폰 지급 본문.")
data class GrantRequest(
    @field:Schema(description = "받을 회원 id(모두 계정 id) 1~100명. 공백·중복은 걷어 낸다", example = "[\"42\", \"57\"]")
    val userIds: List<String>? = null,
)

@Schema(description = "쿠폰 만들기·수정 본문. 수정도 모든 값을 다시 보낸다.")
data class CouponRequest(
    @field:Schema(description = "쿠폰 이름. 1~40자", example = "신규 가입 3,000원 할인")
    val name: String? = null,
    @field:Schema(description = "설명. 선택, 200자 이하", example = "첫 주문에 쓸 수 있어요")
    val description: String? = null,
    @field:Schema(description = "할인 방식: FIXED(정액), PERCENT(정률). 필수", example = "FIXED")
    val discountType: DiscountType? = null,
    @field:Schema(description = "할인 값. 필수. FIXED 면 1원 이상, PERCENT 면 1~90(%)", example = "3000")
    val discountValue: Long? = null,
    @field:Schema(description = "PERCENT 일 때 최대 할인 금액(원). 선택, 1원 이상", example = "5000")
    val maxDiscount: Long? = null,
    @field:Schema(description = "최소 주문 금액(원). 없으면 0", example = "20000")
    val minOrderAmount: Long? = null,
    @field:Schema(description = "적용 범위: ALL(전체 상품), CATEGORY(하위 카테고리 포함), PRODUCT. 없으면 ALL", example = "ALL")
    val scope: CouponScope? = null,
    @field:Schema(description = "scope 가 CATEGORY·PRODUCT 일 때 카테고리·상품 id 1~100개", example = "[3, 5]")
    val scopeIds: List<Long>? = null,
    @field:Schema(description = "발급 시작일. 필수", example = "2026-10-01")
    val issueStart: LocalDate? = null,
    @field:Schema(description = "발급 종료일. 필수, 시작일 이후", example = "2026-10-31")
    val issueEnd: LocalDate? = null,
    @field:Schema(description = "사용 기한(이 날까지). validDays 와 둘 중 하나만. 발급 시작일 이후", example = "2026-11-30")
    val validUntil: LocalDate? = null,
    @field:Schema(description = "받은 날부터 며칠 쓸 수 있는지(1~3650). validUntil 과 둘 중 하나만", example = "30")
    val validDays: Int? = null,
    @field:Schema(description = "총 발급 수량(1장 이상). 없으면 무제한", example = "1000")
    val totalQuantity: Long? = null,
    @field:Schema(description = "쿠폰 코드(코드 등록용). 영문 대문자·숫자 4~20자, 소문자는 대문자로 바꾼다. 없으면 코드 없음", example = "WELCOME2026")
    val code: String? = null,
    @field:Schema(description = "쿠폰존·상품 화면에서 받기(다운로드) 가능 여부. 없으면 false", example = "true")
    val downloadable: Boolean? = null,
    @field:Schema(description = "사용 중 여부. false 면 받을 수 없다(관리자 지급은 된다). 없으면 true", example = "true")
    val active: Boolean? = null,
) {
    fun toCommand() =
        CouponCommand(
            name = name?.trim().orEmpty(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            discountType = requireNotNull(discountType) { "할인 방식을 고르세요." },
            discountValue = requireNotNull(discountValue) { "할인 값을 입력하세요." },
            maxDiscount = maxDiscount,
            minOrderAmount = minOrderAmount ?: 0,
            scope = scope ?: CouponScope.ALL,
            scopeIds = scopeIds.orEmpty().distinct(),
            issueStart = requireNotNull(issueStart) { "발급 시작일을 입력하세요." },
            issueEnd = requireNotNull(issueEnd) { "발급 종료일을 입력하세요." },
            validUntil = validUntil,
            validDays = validDays,
            totalQuantity = totalQuantity,
            code = code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            downloadable = downloadable ?: false,
            active = active ?: true,
        )
}
