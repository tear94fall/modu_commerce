package com.example.commerce.api.common

import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(ex: EntityNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(ex.message ?: "리소스를 찾을 수 없습니다."))

    /** 여러 필드가 틀려도 첫 번째 하나만 알린다. 백오피스 폼이 한 줄로 보여 준다. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message =
            ex.bindingResult.fieldErrors
                .firstOrNull()
                ?.let { "${it.field}: ${it.defaultMessage}" }
                ?: "요청 값이 올바르지 않습니다."
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("요청 본문을 읽을 수 없습니다."))
}

data class ErrorResponse(
    val message: String,
)
