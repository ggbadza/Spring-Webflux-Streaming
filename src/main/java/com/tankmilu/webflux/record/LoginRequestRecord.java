package com.tankmilu.webflux.record;

public record LoginRequestRecord(
        String userId,
        String password
) {
}
