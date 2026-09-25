package com.example.commerce.api.promotion

import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.common.userId
import com.example.commerce.api.product.response.ProductSummaryResponse
import com.example.commerce.application.domain.entity.EventKind
import com.example.commerce.application.domain.entity.PromotionStatus
import com.example.commerce.application.domain.entity.PromotionType
import com.example.commerce.application.usecase.command.PromotionCommand
import com.example.commerce.application.usecase.promotion.CheckAttendanceUseCase
import com.example.commerce.application.usecase.promotion.GetAdminPromotionUseCase
import com.example.commerce.application.usecase.promotion.GetPromotionAttendancesUseCase
import com.example.commerce.application.usecase.promotion.GetPromotionBannersUseCase
import com.example.commerce.application.usecase.promotion.GetPromotionUseCase
import com.example.commerce.application.usecase.promotion.SavePromotionUseCase
import com.example.commerce.application.usecase.promotion.SearchAdminPromotionsUseCase
import com.example.commerce.application.usecase.result.AdminAttendanceResult
import com.example.commerce.application.usecase.result.AdminPromotionDetailResult
import com.example.commerce.application.usecase.result.AdminPromotionSummaryResult
import com.example.commerce.application.usecase.result.AttendanceInfoResult
import com.example.commerce.application.usecase.result.AttendanceResult
import com.example.commerce.application.usecase.result.CouponOfferResult
import com.example.commerce.application.usecase.result.PromotionBannerResult
import com.example.commerce.application.usecase.result.PromotionDetailResult
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

/** 앱: 홈 배너, 기획전·이벤트 상세, 출석 체크. */
@RestController
@RequestMapping("/api/v1/promotions")
class PromotionController(
    private val getPromotionBannersUseCase: GetPromotionBannersUseCase,
    private val getPromotionUseCase: GetPromotionUseCase,
    private val checkAttendanceUseCase: CheckAttendanceUseCase,
) {
    @GetMapping("/banners")
    fun banners(): ResponseEntity<List<PromotionBannerResult>> = ResponseEntity.ok(getPromotionBannersUseCase.execute())

    @GetMapping("/{id}")
    fun promotion(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<PromotionDetailResponse> =
        ResponseEntity.ok(PromotionDetailResponse.from(getPromotionUseCase.execute(jwt.userId(), id)))

    @PostMapping("/{id}/attendance")
    fun attend(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<AttendanceResult> = ResponseEntity.ok(checkAttendanceUseCase.execute(jwt.userId(), id))
}

/** 백오피스: 기획전·이벤트 관리와 출석 현황. */
@RestController
@RequestMapping("/api-admin/v1/promotions")
class AdminPromotionController(
    private val searchAdminPromotionsUseCase: SearchAdminPromotionsUseCase,
    private val getAdminPromotionUseCase: GetAdminPromotionUseCase,
    private val savePromotionUseCase: SavePromotionUseCase,
    private val getPromotionAttendancesUseCase: GetPromotionAttendancesUseCase,
) {
    @GetMapping
    fun promotions(
        @RequestParam(required = false) type: PromotionType?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<AdminPromotionSummaryResult>> =
        ResponseEntity.ok(PageResponse.from(searchAdminPromotionsUseCase.execute(type, q, page, size)) { it })

    @GetMapping("/{id}")
    fun promotion(
        @PathVariable id: Long,
    ): ResponseEntity<AdminPromotionDetailResult> = ResponseEntity.ok(getAdminPromotionUseCase.execute(id))

    @PostMapping
    fun create(
        @RequestBody request: PromotionRequest,
    ): ResponseEntity<AdminPromotionDetailResult> =
        ResponseEntity.status(HttpStatus.CREATED).body(savePromotionUseCase.create(request.toCommand()))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: PromotionRequest,
    ): ResponseEntity<AdminPromotionDetailResult> = ResponseEntity.ok(savePromotionUseCase.update(id, request.toCommand()))

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        savePromotionUseCase.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/attendances")
    fun attendances(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<AdminAttendanceResult>> =
        ResponseEntity.ok(PageResponse.from(getPromotionAttendancesUseCase.execute(id, page, size)) { it })
}

/** 만들기/고치기 본문. 필수 값이 빠지면 400(요청 값이 올바르지 않습니다). */
data class PromotionRequest(
    val type: PromotionType? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val bannerImageUrl: String? = null,
    val bannerColor: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val visible: Boolean? = null,
    val sortOrder: Int? = null,
    val productIds: List<Long>? = null,
    val pointRuleCode: String? = null,
    val rewardPoints: Long? = null,
    val eventKind: EventKind? = null,
    val couponIds: List<Long>? = null,
) {
    fun toCommand(): PromotionCommand {
        val type = requireNotNull(type) { "기획전·이벤트 종류를 고르세요." }
        return PromotionCommand(
            type = type,
            title = title?.trim().orEmpty(),
            subtitle = PromotionCommand.blankToNull(subtitle),
            description = PromotionCommand.blankToNull(description),
            bannerImageUrl = PromotionCommand.blankToNull(bannerImageUrl),
            bannerColor = PromotionCommand.blankToNull(bannerColor),
            startDate = requireNotNull(startDate) { "시작일을 입력하세요." },
            endDate = requireNotNull(endDate) { "종료일을 입력하세요." },
            visible = visible ?: false,
            sortOrder = sortOrder ?: 0,
            productIds = if (type == PromotionType.EXHIBITION) productIds.orEmpty() else emptyList(),
            pointRuleCode = if (type == PromotionType.EVENT) PromotionCommand.blankToNull(pointRuleCode) else null,
            rewardPoints = if (type == PromotionType.EVENT) rewardPoints else null,
            eventKind = if (type == PromotionType.EVENT) eventKind else null,
            couponIds = couponIds.orEmpty(),
        )
    }
}

/** 앱 상세. 기획전 상품은 상품 목록과 같은 모양이다. */
data class PromotionDetailResponse(
    val id: Long,
    val type: PromotionType,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val bannerImageUrl: String?,
    val bannerColor: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: PromotionStatus,
    val products: List<ProductSummaryResponse>,
    val attendance: AttendanceInfoResult?,
    val eventKind: EventKind?,
    val coupons: List<CouponOfferResult>,
) {
    companion object {
        fun from(r: PromotionDetailResult) =
            PromotionDetailResponse(
                r.id,
                r.type,
                r.title,
                r.subtitle,
                r.description,
                r.bannerImageUrl,
                r.bannerColor,
                r.startDate,
                r.endDate,
                r.status,
                r.products.map(ProductSummaryResponse::from),
                r.attendance,
                r.eventKind,
                r.coupons,
            )
    }
}
