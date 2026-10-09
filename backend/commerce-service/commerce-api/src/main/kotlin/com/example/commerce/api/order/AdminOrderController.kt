package com.example.commerce.api.order

import com.example.commerce.api.common.PageResponse
import com.example.commerce.application.domain.entity.OrderStatus
import com.example.commerce.application.usecase.order.ChangeOrderStatusUseCase
import com.example.commerce.application.usecase.order.GetAdminOrderUseCase
import com.example.commerce.application.usecase.order.SearchAdminOrdersUseCase
import com.example.commerce.application.usecase.result.OrderDetailResult
import com.example.commerce.application.usecase.result.OrderSummaryResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Schema(description = "주문 상태 바꾸기 본문.")
data class ChangeStatusRequest(
    @field:Schema(
        description = "바꿀 상태. 필수. 결제 완료(PAID) → SHIPPING·CANCELLED, 배송 중(SHIPPING) → DELIVERED 만 된다",
        example = "SHIPPING",
    )
    @field:NotNull(message = "바꿀 상태를 골라 주세요.")
    val status: OrderStatus? = null,
)

@Tag(
    name = "주문 관리 (어드민)",
    description = "어드민 콘솔용. 게이트웨이 /commerce-service/api-admin/** 가 직원 토큰(ROLE_ADMIN, aud modu-admin)을 확인해 넘긴다.",
)
@RestController
@RequestMapping("/api-admin/v1/orders")
class AdminOrderController(
    private val searchAdminOrdersUseCase: SearchAdminOrdersUseCase,
    private val getAdminOrderUseCase: GetAdminOrderUseCase,
    private val changeOrderStatusUseCase: ChangeOrderStatusUseCase,
) {
    @Operation(
        summary = "주문 목록 검색",
        description = "모든 회원의 주문을 최신 순(id 내림차순)으로 한 페이지 돌려준다. 상태와 주문번호로 거를 수 있다.",
    )
    @GetMapping
    fun orders(
        @Parameter(description = "주문 상태: PAID(결제 완료), SHIPPING(배송 중), DELIVERED(배송 완료), CANCELLED(취소). 없으면 전체", example = "PAID")
        @RequestParam(required = false) status: OrderStatus?,
        @Parameter(description = "주문번호 부분 일치(대소문자 무시)", example = "20260919-4F7K2A")
        @RequestParam(required = false) q: String?,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 15, 1~100 으로 자른다", example = "15")
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<PageResponse<OrderSummaryResult>> =
        ResponseEntity.ok(
            PageResponse.from(searchAdminOrdersUseCase.execute(status, q, page, size)) {
                it
            },
        )

    @Operation(
        summary = "주문 상세 조회",
        description = "주문 상품(주문 당시 스냅샷)·배송지·결제 금액(쿠폰·포인트 포함)을 돌려준다. 없는 주문이면 404.",
    )
    @GetMapping("/{id}")
    fun order(
        @Parameter(description = "주문 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<OrderDetailResult> = ResponseEntity.ok(getAdminOrderUseCase.execute(id))

    @Operation(
        summary = "주문 상태 변경",
        description =
            "PAID → SHIPPING·CANCELLED, SHIPPING → DELIVERED 만 허용하고 그 밖은 400. 없는 주문이면 404. " +
                "취소하면 재고·쿠폰을 되돌리고 결제 포인트는 커밋 뒤 환불한다(point-service 가 죽어 있으면 pointRefundStatus=PENDING, 나중에 다시 보낸다). " +
                "배송 완료면 커밋 뒤 구매 적립을 한다.",
    )
    @PatchMapping("/{id}/status")
    fun changeStatus(
        @Parameter(description = "주문 id", example = "1")
        @PathVariable id: Long,
        @Valid @RequestBody request: ChangeStatusRequest,
    ): ResponseEntity<OrderDetailResult> = ResponseEntity.ok(changeOrderStatusUseCase.execute(id, requireNotNull(request.status)))
}
