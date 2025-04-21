package com.tankmilu.webflux.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationConverter implements ServerAuthenticationConverter {

    private final JwtValidator jwtValidator;

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String token = extractAccessTokenFromCookie(exchange);
        if (token == null || !jwtValidator.validateToken(token)) { // 토큰 인증 실패 시
            return Mono.empty();
        }

        String userId = jwtValidator.extractUserId(token); // 토큰에서 사용자 ID 추출
        String subCode = jwtValidator.extractSessionCode(token);
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();  // todo - Authorities 입력 필요

        CustomUserDetails principal =
                new CustomUserDetails(userId,        // username
                        null,           // password (불필요)
                        grantedAuthorities,
                        subCode);

        // userDetailsService를 통해 실제 사용자 정보를 조회한 후, Authentication 객체를 생성
        return Mono.just(new UsernamePasswordAuthenticationToken(
                principal, null, grantedAuthorities));
    }

    // 헤더에서 토큰 추출
    private String extractAccessTokenFromHeader(HttpHeaders headers) {
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    // 쿠키에서 토큰 추출
    private String extractAccessTokenFromCookie(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst("accessToken");
        return (cookie != null) ? cookie.getValue() : null;
    }

}