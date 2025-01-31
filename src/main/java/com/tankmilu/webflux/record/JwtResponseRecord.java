package com.tankmilu.webflux.record;

import java.util.Date;

public record JwtResponseRecord(
        String accessToken,
        String refreshToken,
        Date createdDate,
        Date accessExpirationDate,
        Date refreshExpirationDate
) {
}
