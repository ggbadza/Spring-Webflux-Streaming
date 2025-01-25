package com.tankmilu.webflux.security;


import com.tankmilu.webflux.entity.UserEntity;
import com.tankmilu.webflux.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtProvider {

    private final UserRepository userRepository;
    @Value("${jwt.secretKey}")
    @Getter
    private String secretKey;

    @Value("${jwt.access.expiration}")
    private Long accessTokenExpirationPeriod;

    @Value("${jwt.refresh.expiration}")
    private Long refreshTokenExpirationPeriod;

    private SecretKey secretKeyHmac;

    public JwtProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @PostConstruct // 의존관계 주입 시 자동 실행
    public void init() {
        this.secretKeyHmac = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public Mono<String> createToken(Authentication authentication) {
        String userId = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        // DB에서 사용자 플랜 조회
        return userRepository.findByUserId(userId)
                .map(user -> {
                    String subscriptionPlan = user.getSubscriptionPlan();

                    Claims claims = Jwts.claims().setSubject(userId).build();
                    claims.put("roles", authorities.stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList()));
                    claims.put("subscriptionPlan", subscriptionPlan);

                    final Date createdDate = new Date();
                    final Date expirationDate = new Date(createdDate.getTime() + accessTokenExpirationPeriod);
                    return Jwts.builder()
                            .setClaims(claims)
                            .setSubject(userId)
                            .setIssuedAt(createdDate)
                            .setExpiration(expirationDate)
                            .signWith(secretKeyHmac)
                            .compact();
                });
    }

    public String createRefreshToken(String username) {
        final Date createdDate = new Date();
        final Date expirationDate = new Date(createdDate.getTime() + refreshTokenExpirationPeriod);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(createdDate)
                .setExpiration(expirationDate)
                .signWith(secretKeyHmac)
                .compact();
    }



}
