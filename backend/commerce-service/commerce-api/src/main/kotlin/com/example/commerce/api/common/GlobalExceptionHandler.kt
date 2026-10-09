package com.example.commerce.api.common

import com.example.commerce.api.point.PointUnavailableException
import com.example.commerce.application.common.logger
import com.example.commerce.application.point.PointGatewayException
import com.example.commerce.application.service.AlreadyCheckedInException
import com.example.commerce.application.service.CouponAlreadyIssuedException
import com.example.commerce.application.service.CouponCodeNotFoundException
import com.example.commerce.application.service.CustomerRequiredException
import com.example.commerce.application.service.PriceChangedException
import com.example.commerce.application.service.PushCampaignStateException
import com.example.commerce.application.service.PushSendFailedException
import com.example.commerce.application.service.TierRunConflictException
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.persistence.EntityNotFoundException
import jakarta.persistence.LockTimeoutException
import jakarta.persistence.PessimisticLockException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 핸들러마다 붙인 @ResponseStatus 는 응답 코드를 바꾸지 않는다(ResponseEntity 가 정한다). springdoc 이 공통 오류 응답(ErrorResponse)을
 * API 문서에 싣도록 알려 주는 용도라 ResponseEntity 의 상태와 같게 맞춘다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(EntityNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(ex: EntityNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(ex.message ?: "리소스를 찾을 수 없습니다."))

    /** 여러 필드가 틀려도 첫 번째 하나만 알린다. 백오피스 폼이 한 줄로 보여 준다. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message =
            ex.bindingResult.fieldErrors
                .firstOrNull()
                ?.let { "${it.field}: ${it.defaultMessage}" }
                ?: "요청 값이 올바르지 않습니다."
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(message))
    }

    /** 도메인 규칙 위반(옵션 조합, 카테고리 깊이, 정가 등). 메시지가 곧 사용자 안내다. */
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(ex.message ?: "요청 값이 올바르지 않습니다."))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("요청 본문을 읽을 수 없습니다."))

    /** point-service 가 죽었거나 내부 토큰이 다를 때. 마이 탭은 포인트 줄만 비우고 나머지는 그대로 보여 준다. */
    @ExceptionHandler(CouponAlreadyIssuedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleCouponIssued(ex: CouponAlreadyIssuedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(ex.message ?: "이미 받은 쿠폰입니다."))

    @ExceptionHandler(CouponCodeNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleCouponCode(ex: CouponCodeNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(ex.message ?: "쿠폰 코드를 확인해 주세요."))

    @ExceptionHandler(AlreadyCheckedInException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleAlreadyChecked(ex: AlreadyCheckedInException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(ex.message ?: "오늘은 이미 출석했습니다."))

    @ExceptionHandler(PushCampaignStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handlePushState(ex: PushCampaignStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(ex.message ?: "예약 상태인 캠페인만 취소할 수 있습니다."))

    @ExceptionHandler(PushSendFailedException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handlePushSend(ex: PushSendFailedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse(ex.message ?: "푸시를 보내지 못했습니다."))

    /** 커머스 가입(약관 동의)이 필요한 API. 앱은 code 를 보고 가입 화면으로 보낸다. */
    @ExceptionHandler(CustomerRequiredException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleCustomerRequired(ex: CustomerRequiredException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(customerRequired())

    @ExceptionHandler(TierRunConflictException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleTierRunConflict(ex: TierRunConflictException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(ex.message ?: "이미 등급 산정이 진행 중입니다."))

    /** point-service 장애·시간 초과·회로 열림. 포인트 없는 주문은 이 경로를 타지 않는다. */
    @ExceptionHandler(PointUnavailableException::class, PointGatewayException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handlePointUnavailable(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse(ex.message ?: PointGatewayException.MESSAGE))

    /** 화면이 본 결제 금액이 서버 계산과 다르다. 앱은 문구를 보이고 새 금액으로 주문서를 다시 그린다. */
    @ExceptionHandler(PriceChangedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handlePriceChanged(ex: PriceChangedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(PriceChangedException.MESSAGE, PriceChangedException.CODE, paymentAmount = ex.paymentAmount))

    /** 유니크 제약 등에 걸림 — 같은 요청이 동시에 두 번 처리된 경우가 대부분이다. */
    @ExceptionHandler(DataIntegrityViolationException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleDataIntegrity(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        logger.warn { "data integrity violation: ${ex.mostSpecificCause.message}" }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(DUPLICATE_MESSAGE))
    }

    /** 행 잠금을 시간 안에 못 잡음(같은 주문·옵션에 요청이 몰림). 잠시 뒤 다시 하면 된다. */
    @ExceptionHandler(PessimisticLockingFailureException::class, PessimisticLockException::class, LockTimeoutException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleLockFailure(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.warn { "lock not acquired: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse(BUSY_MESSAGE))
    }

    companion object {
        const val DUPLICATE_MESSAGE = "이미 처리된 요청이에요."
        const val BUSY_MESSAGE = "요청이 몰려 처리하지 못했어요. 잠시 후 다시 시도해 주세요."
    }
}

/**
 * [code] 는 앱이 분기할 때만 붙는다(CUSTOMER_REQUIRED, PRICE_CHANGED). [paymentAmount] 는 PRICE_CHANGED 의 새 결제 금액.
 * null 인 필드는 JSON 에서 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val message: String,
    val code: String? = null,
    val paymentAmount: Long? = null,
)

fun customerRequired() = ErrorResponse(CustomerRequiredException.MESSAGE, CustomerRequiredException.CODE)
