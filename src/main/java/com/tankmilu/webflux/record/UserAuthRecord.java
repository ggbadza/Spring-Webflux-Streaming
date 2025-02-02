package com.tankmilu.webflux.record;

import java.util.List;

public record UserAuthRecord(
        String userId,
        List<String> roles,
        String subscriptionPlan,
        String sessionCode
) {
}
