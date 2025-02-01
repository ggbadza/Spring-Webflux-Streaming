package com.tankmilu.webflux.record;

public record UserRegRequests(
        String userId,
        String userName,
        String password,
        String subscriptionPlan) {
}
