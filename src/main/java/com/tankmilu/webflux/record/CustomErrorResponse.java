package com.tankmilu.webflux.record;

import org.springframework.http.HttpStatus;

public record CustomErrorResponse(
        String errorCode,
        String errorMsg) {
}
