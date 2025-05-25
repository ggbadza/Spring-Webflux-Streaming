package com.tankmilu.webflux.record;

public record UserRegRequest(
        String userId,
        String userName,
        String password,
        String regCode) {
}
