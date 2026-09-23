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

    /** 차감을 되돌린다(주문 취소). [refId] 는 멱등 키. */
    fun refund(
        userId: String,
        amount: Long,
        refId: String,
        memo: String?,
    )
}

/** 잔액 부족. 메시지가 곧 사용자 안내라 400 으로 나간다. */
class InsufficientPointException : IllegalArgumentException("포인트가 부족합니다. 잔액을 확인해 주세요.")

/** 포인트 서버 장애. 주문은 만들지 않는다(503). */
class PointGatewayException(
    cause: Throwable? = null,
) : RuntimeException("포인트 서비스에 연결할 수 없습니다.", cause)
