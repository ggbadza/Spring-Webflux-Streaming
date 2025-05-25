package com.tankmilu.webflux.exception;

import org.springframework.http.HttpStatus;

public class DuplicateUserException extends UserException {
  protected DuplicateUserException(String message, String errorCode, HttpStatus httpStatus) {
    super(message, errorCode, httpStatus);
  }
}
