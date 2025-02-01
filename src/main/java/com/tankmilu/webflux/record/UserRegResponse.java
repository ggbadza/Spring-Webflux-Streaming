package com.tankmilu.webflux.record;

public record UserRegResponse(
        String userId,
        String userName,
        String subscriptionPlan,
        String msg) {
}
