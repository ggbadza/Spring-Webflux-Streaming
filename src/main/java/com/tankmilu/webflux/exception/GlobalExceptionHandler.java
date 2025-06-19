package com.tankmilu.webflux.exception;

import com.tankmilu.webflux.record.CustomErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

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

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new CustomErrorResponse("SERVER_ERROR", "서버 오류가 발생했습니다."));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<CustomErrorResponse> handleServerWebInputException(ServerWebInputException ex) {
        System.err.println("Client input error: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new CustomErrorResponse("BAD_REQUEST", ex.getReason()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<CustomErrorResponse> handleWebExchangeBindException(WebExchangeBindException ex) {
        System.err.println("Request body binding error: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new CustomErrorResponse("BAD_REQUEST", ex.getReason()));
    }

}