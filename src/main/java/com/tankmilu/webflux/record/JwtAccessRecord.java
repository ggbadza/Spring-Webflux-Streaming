package com.tankmilu.webflux.record;

import java.time.LocalDateTime;

public record JwtAccessRecord(
        String accessToken,
        LocalDateTime createdDate,
        LocalDateTime accessExpirationDate) {
}
