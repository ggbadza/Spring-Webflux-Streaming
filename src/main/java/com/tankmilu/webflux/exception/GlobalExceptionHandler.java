package com.tankmilu.webflux.exception;

import com.tankmilu.webflux.record.CustomErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserException.class)
    public ResponseEntity<CustomErrorResponse> handleUserException(UserException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(new CustomErrorResponse(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomErrorResponse> handleGenericException(Exception e) {
        return ResponseEntity.status(500)
                .body(new CustomErrorResponse("SERVER_ERROR", "서버 오류가 발생했습니다."));
    }
}