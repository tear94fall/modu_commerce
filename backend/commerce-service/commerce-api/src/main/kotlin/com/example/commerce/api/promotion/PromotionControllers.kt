package com.example.commerce.api.promotion

import com.example.commerce.api.common.CustomerRequired
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

/** 앱: 홈 배너, 기획전·이벤트 상세, 출석 체크. */
@Tag(
    name = "기획전·이벤트 (앱)",
    description =
        "커머스 웹/앱(웹뷰)이 같은 출처 /api 로 부른다(nginx → commerce-service). 모두 계정 토큰(aud modu-commerce) 필요. " +
            "배너·상세는 커머스 가입 전에도 보고, 출석 체크는 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
)
@RestController
@RequestMapping("/api/v1/promotions")
class PromotionController(
    private val getPromotionBannersUseCase: GetPromotionBannersUseCase,
    private val getPromotionUseCase: GetPromotionUseCase,
    private val checkAttendanceUseCase: CheckAttendanceUseCase,
) {
    @Operation(
        summary = "홈 배너 조회",
        description = "노출 중이고 오늘이 기간 안인 기획전·이벤트를 순서(sortOrder)·최신 순으로 돌려준다. 오늘 날짜 키로 캐시한다.",
    )
    @GetMapping("/banners")
    fun banners(): ResponseEntity<List<PromotionBannerResult>> = ResponseEntity.ok(getPromotionBannersUseCase.execute())

    @Operation(
        summary = "기획전·이벤트 상세 조회",
        description =
            "기획전이면 판매 중인 상품(관리자 순서, 찜 여부 포함), 출석 이벤트면 내 출석 날짜·오늘 출석 여부, 쿠폰 이벤트면 쿠폰과 받았는지를 함께 돌려준다. " +
                "지난·예정 이벤트도 상태(status)와 함께 보인다. 없거나 노출이 꺼졌으면 404.",
    )
    @GetMapping("/{id}")
    fun promotion(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "기획전·이벤트 id", example = "4")
        @PathVariable id: Long,
    ): ResponseEntity<PromotionDetailResponse> =
        ResponseEntity.ok(PromotionDetailResponse.from(getPromotionUseCase.execute(jwt.userId(), id)))

    @Operation(
        summary = "출석 체크",
        description =
            "진행 중인 출석 이벤트에 오늘 출석을 남기고, 포인트 규칙이 있으면 적립한다(한도 초과 등으로 적립이 안 되면 0P 와 이유). " +
                "없거나 숨긴 이벤트면 404, 출석 이벤트가 아니거나 진행 중이 아니면 400, 오늘 이미 출석했으면 409, " +
                "포인트 서버를 못 부르면 503(출석도 남기지 않음). 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @PostMapping("/{id}/attendance")
    fun attend(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "출석 이벤트 id", example = "5")
        @PathVariable id: Long,
    ): ResponseEntity<AttendanceResult> = ResponseEntity.ok(checkAttendanceUseCase.execute(jwt.userId(), id))
}

/** 백오피스: 기획전·이벤트 관리와 출석 현황. */
@Tag(
    name = "기획전·이벤트 관리 (어드민)",
    description = "어드민 콘솔용. 게이트웨이 /commerce-service/api-admin/** 가 직원 토큰(ROLE_ADMIN, aud modu-admin)을 확인해 넘긴다.",
)
@RestController
@RequestMapping("/api-admin/v1/promotions")
class AdminPromotionController(
    private val searchAdminPromotionsUseCase: SearchAdminPromotionsUseCase,
    private val getAdminPromotionUseCase: GetAdminPromotionUseCase,
    private val savePromotionUseCase: SavePromotionUseCase,
    private val getPromotionAttendancesUseCase: GetPromotionAttendancesUseCase,
) {
    @Operation(
        summary = "기획전·이벤트 목록 검색",
        description =
            "지우지 않은 기획전·이벤트를 정렬 순서(sortOrder) 오름차순, 같으면 최신 순으로 한 페이지 돌려준다. " +
                "진행 상태(오늘 KST 기준)와 출석 수가 붙는다.",
    )
    @GetMapping
    fun promotions(
        @Parameter(description = "종류: EXHIBITION(기획전), EVENT(이벤트). 없으면 전체", example = "EVENT")
        @RequestParam(required = false) type: PromotionType?,
        @Parameter(description = "제목 부분 일치(대소문자 무시)", example = "출석")
        @RequestParam(required = false) q: String?,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 20, 1~100 으로 자른다", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<AdminPromotionSummaryResult>> =
        ResponseEntity.ok(PageResponse.from(searchAdminPromotionsUseCase.execute(type, q, page, size)) { it })

    @Operation(
        summary = "기획전·이벤트 상세 조회",
        description = "기획전 상품(숨긴 상품 포함)·연결 쿠폰·보상 설정·출석 수까지 돌려준다. 없거나 지운 기획전이면 404.",
    )
    @GetMapping("/{id}")
    fun promotion(
        @Parameter(description = "기획전·이벤트 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<AdminPromotionDetailResult> = ResponseEntity.ok(getAdminPromotionUseCase.execute(id))

    @Operation(
        summary = "기획전·이벤트 만들기",
        description =
            "만들고 201 과 상세를 돌려준다. 앱 홈 배너 캐시를 비운다. 제목·기간·상품 수·쿠폰 수 규칙을 어기거나 " +
                "없는 상품·쿠폰이 있으면 400.",
    )
    @PostMapping
    fun create(
        @RequestBody request: PromotionRequest,
    ): ResponseEntity<AdminPromotionDetailResult> =
        ResponseEntity.status(HttpStatus.CREATED).body(savePromotionUseCase.create(request.toCommand()))

    @Operation(
        summary = "기획전·이벤트 수정",
        description =
            "모든 값을 다시 보내 통째로 바꾸고 배너·상세 캐시를 비운다. 종류(type)와 이벤트 종류(eventKind)는 바꿀 수 없다(400). " +
                "없는 기획전이면 404.",
    )
    @PutMapping("/{id}")
    fun update(
        @Parameter(description = "기획전·이벤트 id", example = "1")
        @PathVariable id: Long,
        @RequestBody request: PromotionRequest,
    ): ResponseEntity<AdminPromotionDetailResult> = ResponseEntity.ok(savePromotionUseCase.update(id, request.toCommand()))

    @Operation(
        summary = "기획전·이벤트 삭제",
        description = "소프트 삭제하고 204 를 돌려준다. 배너·상세 캐시를 비운다. 없는 기획전이면 404.",
    )
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(description = "기획전·이벤트 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        savePromotionUseCase.delete(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "출석 현황 조회",
        description = "이 이벤트의 출석 기록(회원·날짜·적립 포인트)을 최신 순으로 한 페이지 돌려준다. 없는 기획전이면 404.",
    )
    @GetMapping("/{id}/attendances")
    fun attendances(
        @Parameter(description = "출석 이벤트 id", example = "5")
        @PathVariable id: Long,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 20, 1~100 으로 자른다", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<AdminAttendanceResult>> =
        ResponseEntity.ok(PageResponse.from(getPromotionAttendancesUseCase.execute(id, page, size)) { it })
}

/** 만들기/고치기 본문. 필수 값이 빠지면 400(요청 값이 올바르지 않습니다). */
@Schema(description = "기획전·이벤트 만들기·수정 본문. 수정도 모든 값을 다시 보낸다. 종류에 맞지 않는 값은 무시한다.")
data class PromotionRequest(
    @field:Schema(description = "종류: EXHIBITION(기획전), EVENT(이벤트). 필수, 수정 때 바꿀 수 없다", example = "EXHIBITION")
    val type: PromotionType? = null,
    @field:Schema(description = "제목. 1~60자", example = "가을 문구 기획전")
    val title: String? = null,
    @field:Schema(description = "부제. 선택, 100자 이하", example = "최대 30% 할인")
    val subtitle: String? = null,
    @field:Schema(description = "설명. 선택, 2000자 이하", example = "새 학기 필기구를 모았어요.")
    val description: String? = null,
    @field:Schema(description = "홈 배너 이미지 주소. 선택, 500자 이하", example = "https://example.com/banner/fall.jpg")
    val bannerImageUrl: String? = null,
    @field:Schema(description = "배너 배경색 #RRGGBB. 선택", example = "#FDE68A")
    val bannerColor: String? = null,
    @field:Schema(description = "시작일(KST 날짜). 필수", example = "2026-10-01")
    val startDate: LocalDate? = null,
    @field:Schema(description = "종료일(이 날 포함). 필수, 시작일 이후", example = "2026-10-31")
    val endDate: LocalDate? = null,
    @field:Schema(description = "앱에 노출할지. 없으면 false", example = "true")
    val visible: Boolean? = null,
    @field:Schema(description = "정렬 순서(작을수록 앞). 없으면 0", example = "0")
    val sortOrder: Int? = null,
    @field:Schema(description = "기획전 상품 id 1~100개(중복 불가). 기획전만 쓴다", example = "[1, 2, 3]")
    val productIds: List<Long>? = null,
    @field:Schema(description = "출석 체크 보상으로 적립할 point-service 규칙 코드. 출석 이벤트만 쓴다. 없으면 보상 없음", example = "ATTENDANCE")
    val pointRuleCode: String? = null,
    @field:Schema(description = "화면에 보여 줄 보상 포인트(0 이상). 실제 적립은 규칙이 정한다. pointRuleCode 가 없으면 무시", example = "10")
    val rewardPoints: Long? = null,
    @field:Schema(description = "이벤트 종류: ATTENDANCE(출석 체크), COUPON(쿠폰 받기). 이벤트만 쓰고, 없으면 ATTENDANCE. 수정 때 바꿀 수 없다", example = "ATTENDANCE")
    val eventKind: EventKind? = null,
    @field:Schema(description = "연결할 쿠폰 id 최대 10개(중복 불가). 쿠폰 이벤트는 1개 이상 필수, 출석 이벤트는 무시", example = "[7]")
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
