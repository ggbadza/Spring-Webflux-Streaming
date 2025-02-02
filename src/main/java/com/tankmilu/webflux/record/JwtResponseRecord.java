package com.tankmilu.webflux.record;

import java.time.LocalDateTime;
import java.util.Date;

public record JwtResponseRecord(
        String accessToken,
        String refreshToken,
        LocalDateTime createdDate,
        LocalDateTime accessExpirationDate,
        LocalDateTime refreshExpirationDate
) {
}
