package com.tankmilu.webflux.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationConverter implements ServerAuthenticationConverter {

    private final JwtValidator jwtValidator;

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String token = extractToken(exchange.getRequest().getHeaders());
        if (token == null || !jwtValidator.validateToken(token)) { // 토큰 인증 실패 시
            return Mono.empty();
        }

        String userId = jwtValidator.extractUserId(token); //토큰 검증 후 ID 추출
        Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
        return Mono.just(auth);
    }

    private String extractToken(HttpHeaders headers) {
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}