package com.tankmilu.webflux.exception;

import org.springframework.http.HttpStatus;

public class ContentsNotFoundException extends UserException {
    public ContentsNotFoundException(String message, String errorCode, HttpStatus httpStatus) {
      super(message, errorCode, httpStatus);
    }
}
