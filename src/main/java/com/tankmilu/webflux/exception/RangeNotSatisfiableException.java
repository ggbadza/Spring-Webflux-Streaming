package com.tankmilu.webflux.exception;

import org.springframework.http.HttpStatus;

public class RangeNotSatisfiableException  extends UserException {
    public RangeNotSatisfiableException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    public RangeNotSatisfiableException(String message) {
        super(message, "1002", HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }
}