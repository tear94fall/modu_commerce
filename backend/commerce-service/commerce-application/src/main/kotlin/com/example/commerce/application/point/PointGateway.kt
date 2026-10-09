package com.example.commerce.application.point

/** 포인트 서버(모두 챗 point-service)에 차감·환불을 요청하는 포트. 구현(commerce-api)은 내부 API 를 RestClient 로 부른다. */
interface PointGateway {
    /**
     * [amount] 만큼 차감. [refId] 는 멱등 키(주문번호)라 같은 키로 다시 오면 차감하지 않는다.
     * 잔액이 모자라면 [InsufficientPointException], 서버를 못 부르면 [PointGatewayException].
     */
    fun spend(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    )

    /**
     * 규칙([ruleCode])대로 적립한다. 금액·하루 한도는 point-service 규칙이 정한다. [refId] 는 멱등 키.
     * 한도·중복·규칙 없음 같은 "적립 안 됨"은 예외가 아니라 [PointEarnResult.applied] = false 로 온다.
     * 서버를 못 부르면 [PointGatewayException].
     */
    fun earn(
        userId: String,
        ruleCode: String,
        refId: String,
        memo: String?,
    ): PointEarnResult

    /**
     * 규칙 없이 [amount] 만큼 적립한다(구매 적립). [refId] 는 멱등 키라 같은 키로 다시 오면 applied=false, reason=DUPLICATE.
     * 서버를 못 부르면 [PointGatewayException], 요청을 거절하면(4xx) [PointEarnRejectedException].
     */
    fun earnAmount(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    ): PointEarnResult

    /** 차감을 되돌린다(주문 취소). [refId] 는 멱등 키. */
    fun refund(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    )

    /**
     * 차감 [spendRefId](`order:<번호>`)가 있으면 되돌린다. point-service 는 `refund:` + spendRefId 로 돌려주므로 [refund] 와 겹쳐도
     * 한 번만 돌아간다. 차감이 없으면 cancelled=false, reason=NO_SPEND. 서버를 못 부르면 [PointGatewayException].
     */
    fun cancelSpend(
        userId: String,
        spendRefId: String,
        memo: String?,
    ): PointCancelResult

    /** 원장에 있는 거래만 돌려준다(대사용). 한 번에 [MAX_REFS] 개까지. 서버를 못 부르면 [PointGatewayException]. */
    fun findTransactions(refs: List<PointRef>): List<PointRefTransaction>

    companion object {
        const val MAX_REFS = 500
    }
}

/** spend/cancel 결과. [reason]: NO_SPEND(차감 없음), ALREADY_REFUNDED(이미 돌려줌), 되돌렸으면 null. */
data class PointCancelResult(
    val cancelled: Boolean,
    val reason: String?,
    val amount: Long,
    val balance: Long,
) {
    companion object {
        const val NO_SPEND = "NO_SPEND"
        const val ALREADY_REFUNDED = "ALREADY_REFUNDED"
    }
}

/** 원장 조회 키. */
data class PointRef(
    val userId: String,
    val refId: String,
)

/** 원장 거래 한 건(point-service 의 refs 응답). [amount] 는 원장 그대로(차감은 음수일 수 있다). */
data class PointRefTransaction(
    val userId: String,
    val refId: String,
    val type: String,
    val amount: Long,
    val createdDate: String?,
)

/** 잔액 부족. 메시지가 곧 사용자 안내라 400 으로 나간다. */
class InsufficientPointException : IllegalArgumentException("포인트가 부족합니다. 잔액을 확인해 주세요.")

/** 포인트 서버 장애(연결 실패·시간 초과·회로 열림). 포인트를 쓰는 주문은 만들지 않는다(503). */
class PointGatewayException(
    cause: Throwable? = null,
) : RuntimeException(MESSAGE, cause) {
    companion object {
        const val MESSAGE = "지금은 포인트를 쓸 수 없어요. 포인트 없이 주문하거나 잠시 후 다시 시도해 주세요."
    }
}

/** 포인트 서버가 적립 요청을 거절했다(잘못된 값). 다시 보내도 같으니 재시도하지 않는다. */
class PointEarnRejectedException(
    message: String,
) : RuntimeException(message)

/** 적립 결과. [reason] 은 적립되지 않은 이유(point-service 의 RULE_DISABLED, DAILY_LIMIT, TOTAL_LIMIT, DUPLICATE, 또는 RULE_NOT_FOUND). */
data class PointEarnResult(
    val applied: Boolean,
    val amount: Long,
    val reason: String? = null,
)
