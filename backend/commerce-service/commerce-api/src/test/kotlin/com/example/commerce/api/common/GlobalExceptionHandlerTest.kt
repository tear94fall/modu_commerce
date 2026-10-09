package com.example.commerce.api.common

import com.example.commerce.application.service.PriceChangedException
import jakarta.persistence.LockTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `유니크 제약 위반은 409 이미 처리된 요청`() {
        val response = handler.handleDataIntegrity(DataIntegrityViolationException("Duplicate entry"))
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorResponse("이미 처리된 요청이에요."), response.body)
    }

    @Test
    fun `잠금을 못 잡으면 503 요청이 몰림`() {
        listOf(
            PessimisticLockingFailureException("lock"),
            CannotAcquireLockException("Lock wait timeout exceeded"),
            LockTimeoutException("timeout"),
        ).forEach {
            val response = handler.handleLockFailure(it)
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
            assertEquals("요청이 몰려 처리하지 못했어요. 잠시 후 다시 시도해 주세요.", response.body?.message)
        }
    }

    @Test
    fun `가격이 바뀌면 409 PRICE_CHANGED 와 새 금액`() {
        val response = handler.handlePriceChanged(PriceChangedException(15_000))
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorResponse("가격이 바뀌었어요. 결제 금액을 다시 확인해 주세요.", "PRICE_CHANGED", 15_000), response.body)
    }
}
