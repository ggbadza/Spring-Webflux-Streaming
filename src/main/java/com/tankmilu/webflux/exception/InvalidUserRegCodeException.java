package com.tankmilu.webflux.exception;

import org.springframework.http.HttpStatus;

public class InvalidUserRegCodeException extends UserException {
    public InvalidUserRegCodeException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    public InvalidUserRegCodeException(String message) {
        super(message, "1001", HttpStatus.BAD_REQUEST);
    }
}
