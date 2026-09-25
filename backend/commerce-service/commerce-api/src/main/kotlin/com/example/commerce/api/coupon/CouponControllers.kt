package com.example.commerce.api.coupon

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
@RestController
@RequestMapping("/api/v1")
class CouponController(
    private val myCouponsUseCase: MyCouponsUseCase,
    private val issueCouponUseCase: IssueCouponUseCase,
    private val applicableCouponsUseCase: ApplicableCouponsUseCase,
) {
    @GetMapping("/me/coupons")
    fun myCoupons(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "AVAILABLE") status: UserCouponStatus,
    ): ResponseEntity<List<MyCouponResult>> = ResponseEntity.ok(myCouponsUseCase.list(jwt.userId(), status))

    @GetMapping("/me/coupons/count")
    fun count(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Map<String, Int>> = ResponseEntity.ok(mapOf("available" to myCouponsUseCase.availableCount(jwt.userId())))

    @GetMapping("/coupons/downloadable")
    fun downloadable(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) productId: Long?,
    ): ResponseEntity<List<CouponOfferResult>> = ResponseEntity.ok(myCouponsUseCase.downloadable(jwt.userId(), productId))

    @PostMapping("/coupons/{couponId}/download")
    fun download(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable couponId: Long,
    ): ResponseEntity<MyCouponResult> = ResponseEntity.ok(issueCouponUseCase.download(jwt.userId(), couponId))

    @PostMapping("/coupons/redeem")
    fun redeem(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: RedeemRequest,
    ): ResponseEntity<MyCouponResult> {
        val code = requireNotNull(request.code?.takeIf { it.isNotBlank() }) { "쿠폰 코드를 입력하세요." }
        return ResponseEntity.ok(issueCouponUseCase.redeem(jwt.userId(), code))
    }

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

    @PostMapping("/promotions/{id}/coupons")
    fun claimEvent(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<CouponClaimResult> = ResponseEntity.ok(issueCouponUseCase.claimEvent(jwt.userId(), id))
}

data class RedeemRequest(
    val code: String? = null,
)

data class ApplicableLine(
    val skuId: Long? = null,
    val quantity: Int? = null,
)

data class ApplicableRequest(
    val items: List<ApplicableLine>? = null,
)

/** 백오피스: 쿠폰 관리, 발급 현황, 회원 지급. */
@RestController
@RequestMapping("/api-admin/v1/coupons")
class AdminCouponController(
    private val adminCouponUseCase: AdminCouponUseCase,
) {
    @GetMapping
    fun coupons(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<PageResponse<AdminCouponSummaryResult>> =
        ResponseEntity.ok(PageResponse.from(adminCouponUseCase.search(q, active, page, size)) { it })

    @GetMapping("/{id}")
    fun coupon(
        @PathVariable id: Long,
    ): ResponseEntity<AdminCouponDetailResult> = ResponseEntity.ok(adminCouponUseCase.detail(id))

    @PostMapping
    fun create(
        @RequestBody request: CouponRequest,
    ): ResponseEntity<AdminCouponDetailResult> =
        ResponseEntity.status(HttpStatus.CREATED).body(adminCouponUseCase.create(request.toCommand()))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: CouponRequest,
    ): ResponseEntity<AdminCouponDetailResult> = ResponseEntity.ok(adminCouponUseCase.update(id, request.toCommand()))

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        adminCouponUseCase.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/issues")
    fun issues(
        @PathVariable id: Long,
        @RequestParam(required = false) status: UserCouponStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<PageResponse<AdminCouponIssueResult>> =
        ResponseEntity.ok(PageResponse.from(adminCouponUseCase.issues(id, status, page, size)) { it })

    @PostMapping("/{id}/issues")
    fun grant(
        @PathVariable id: Long,
        @RequestBody request: GrantRequest,
    ): ResponseEntity<CouponGrantResult> = ResponseEntity.ok(adminCouponUseCase.grant(id, request.userIds.orEmpty()))
}

data class GrantRequest(
    val userIds: List<String>? = null,
)

data class CouponRequest(
    val name: String? = null,
    val description: String? = null,
    val discountType: DiscountType? = null,
    val discountValue: Long? = null,
    val maxDiscount: Long? = null,
    val minOrderAmount: Long? = null,
    val scope: CouponScope? = null,
    val scopeIds: List<Long>? = null,
    val issueStart: LocalDate? = null,
    val issueEnd: LocalDate? = null,
    val validUntil: LocalDate? = null,
    val validDays: Int? = null,
    val totalQuantity: Long? = null,
    val code: String? = null,
    val downloadable: Boolean? = null,
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
