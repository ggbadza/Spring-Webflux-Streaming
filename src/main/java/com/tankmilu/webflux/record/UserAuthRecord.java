package com.tankmilu.webflux.record;

import java.util.List;
import java.util.UUID;

public record UserAuthRecord(
        String userId,
        List<String> roles,
        String subscriptionPlan,
        String sessionCode
) {
}
