package com.tankmilu.webflux.record;

import java.time.LocalDateTime;

public record JwtAccessAndRefreshRecord(
        String accessToken,
        String refreshToken,
        LocalDateTime createdDate,
        LocalDateTime accessExpirationDate,
        LocalDateTime refreshExpirationDate
) {
}
