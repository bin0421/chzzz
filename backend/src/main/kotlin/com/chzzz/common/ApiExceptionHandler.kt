package com.chzzz.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(FieldValidationException::class)
    fun handleFieldValidation(e: FieldValidationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요.").apply {
            title = "잘못된 요청"
            setProperty("errors", e.errors)
        }

    @ExceptionHandler(FieldConflictException::class)
    fun handleFieldConflict(e: FieldConflictException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "이미 사용 중인 값이 있습니다.").apply {
            title = "상태 충돌"
            setProperty("errors", e.errors)
        }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message ?: "찾을 수 없습니다.").apply {
            title = "찾을 수 없음"
        }

    @ExceptionHandler(UnauthenticatedException::class)
    fun handleUnauthenticated(e: UnauthenticatedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.message ?: "로그인이 필요합니다.").apply {
            title = "로그인 필요"
        }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(e: ForbiddenException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.message ?: "권한이 없습니다.").apply {
            title = "권한 없음"
        }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(e: ConflictException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message ?: "현재 상태에서는 할 수 없습니다.").apply {
            title = "상태 충돌"
        }
}
