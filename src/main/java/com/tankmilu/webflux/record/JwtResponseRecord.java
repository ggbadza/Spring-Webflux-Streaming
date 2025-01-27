package com.tankmilu.webflux.record;

public record JwtResponseRecord(
        String accessToken,
        String refreshToken
) {
}
