package com.tankmilu.webflux.exception;

import com.tankmilu.webflux.record.CustomErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UserException.class)
    public ResponseEntity<CustomErrorResponse> handleUserException(UserException e) {
        log.warn("User exception occurred: {} - {}", e.getErrorCode(), e.getMessage());

        return ResponseEntity.status(e.getHttpStatus())
                .body(new CustomErrorResponse(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomErrorResponse> handleGenericException(Exception e) {
        log.error("Unexpected error occurred", e);

        return ResponseEntity.status(500)
                .body(new CustomErrorResponse("SERVER_ERROR", "서버 오류가 발생했습니다."));
    }
}