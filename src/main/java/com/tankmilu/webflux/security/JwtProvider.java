package com.tankmilu.webflux.security;


import com.tankmilu.webflux.entity.JwtRefreshTokenEntity;
import com.tankmilu.webflux.record.JwtResponseRecord;
import com.tankmilu.webflux.record.UserAuthRecord;
import com.tankmilu.webflux.repository.JwtRefreshTokenRepository;
import com.tankmilu.webflux.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final UserRepository userRepository;

    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;

    @Value("${jwt.secretKey}")
    private String secretKey;

    @Value("${jwt.access.expiration}")
    private Long accessTokenExpirationPeriod;

    @Value("${jwt.refresh.expiration}")
    private Long refreshTokenExpirationPeriod;

    @Getter
    private SecretKey secretKeyHmac;

    @PostConstruct // 의존관계 주입 시 자동 실행
    public void init() {
        byte[] decodedKey = Base64.getDecoder().decode(secretKey);
        this.secretKeyHmac = Keys.hmacShaKeyFor(decodedKey);
    }

    @Transactional
    public Mono<JwtResponseRecord> createToken(Authentication authentication) {
        String userId = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        // DB에서 사용자 플랜 조회
        return userRepository.findByUserId(userId)
                .flatMap(user -> {
                    String subscriptionPlan = user.getSubscriptionPlan();

                    Claims claims = Jwts.claims().setSubject(userId).build();
                    claims.put("roles", authorities.stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList()));
                    claims.put("subscriptionPlan", subscriptionPlan);

                    final Date createdDate = new Date();
                    final Date accessExpirationDate = new Date(createdDate.getTime() + accessTokenExpirationPeriod);
                    final Date refreshExpirationDate = new Date(createdDate.getTime() + accessTokenExpirationPeriod);
                    String accessToken =
                            Jwts.builder()
                            .setClaims(claims)
                            .setSubject(userId)
                            .setIssuedAt(createdDate)
                            .setExpiration(accessExpirationDate)
                            .signWith(secretKeyHmac)
                            .compact();
                    // 무작위 세션코드 생성
                    String sessionCode = UUID.randomUUID().toString();
                    // 리프레시 토큰 엔티티 생성
                    JwtRefreshTokenEntity jwtRefreshTokenEntity =
                            JwtRefreshTokenEntity
                            .builder()
                            .sessionCode(sessionCode)
                            .userId(userId)
                            .issuedAt(createdDate)
                            .expiredAt(refreshExpirationDate)
                            .build();

                    // 리프레시 토큰 발급
                    return jwtRefreshTokenRepository.save(jwtRefreshTokenEntity).zipWhen(saved -> Mono.just(Tuples.of(accessToken, sessionCode, createdDate, refreshExpirationDate)));})
                .map(tuple2 -> {
                    Tuple4<String, String, Date, Date> data = tuple2.getT2();
                    String accessToken = data.getT1();
                    String sessionCode = data.getT2();
                    Date createdDate = data.getT3();
                    Date refreshExpirationDate = data.getT4();

                    String refreshToken =
                            Jwts.builder()
                                    .setSubject(sessionCode)
                                    .setIssuedAt(createdDate)
                                    .setExpiration(refreshExpirationDate)
                                    .signWith(secretKeyHmac)
                                    .compact();
                    return new JwtResponseRecord(accessToken,refreshToken,createdDate,null,refreshExpirationDate);
                });
    }

    public JwtResponseRecord createAccessToken(UserAuthRecord userAuthRecord) {

        final Date createdDate = new Date();
        final Date accessExpirationDate = new Date(createdDate.getTime() + accessTokenExpirationPeriod);

        Claims claims = Jwts.claims().setSubject(userAuthRecord.userId()).build();
        claims.put("roles", userAuthRecord.roles());
        claims.put("subscriptionPlan", userAuthRecord.subscriptionPlan());

        String accessToken=Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(createdDate)
                .setExpiration(accessExpirationDate)
                .signWith(secretKeyHmac)
                .compact();

        return new JwtResponseRecord(accessToken,null,createdDate,accessExpirationDate,null);
    }

    public JwtResponseRecord createRefreshToken(UserAuthRecord userAuthRecord) {

        final Date createdDate = new Date();
        final Date refreshExpirationDate = new Date(createdDate.getTime() + accessTokenExpirationPeriod);


        String refreshToken=Jwts.builder()
                .subject(userAuthRecord.userId())
                .claims()
                .add("sessionCode", userAuthRecord.sessionCode()) // 세션 코드 추가
                .issuedAt(createdDate)
                .expiration(refreshExpirationDate)
                .and() // 클레임 설정 종료
                .signWith(secretKeyHmac)
                .compact();

        return new JwtResponseRecord(null,refreshToken,createdDate,null,refreshExpirationDate);
    }

}
