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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

    public JwtResponseRecord createAccessToken(UserAuthRecord userAuthRecord) {

        LocalDateTime createdDate = LocalDateTime.now();
        LocalDateTime accessExpirationDate = createdDate.plus(accessTokenExpirationPeriod, ChronoUnit.MILLIS);

        String accessToken=Jwts.builder()
                .subject(userAuthRecord.userId())
                .claim("roles", userAuthRecord.roles())
                .claim("subscriptionPlan", userAuthRecord.subscriptionPlan())
                .claim("nonce", UUID.randomUUID().toString())
                .issuedAt(Date.from(createdDate.atZone(ZoneId.systemDefault()).toInstant()))
                .expiration(Date.from(accessExpirationDate.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(secretKeyHmac)
                .compact();


        return new JwtResponseRecord(accessToken,null,createdDate,accessExpirationDate,null);
    }

    public JwtResponseRecord createRefreshToken(UserAuthRecord userAuthRecord) {


        LocalDateTime createdDate = LocalDateTime.now();
        LocalDateTime refreshExpirationDate = createdDate.plus(accessTokenExpirationPeriod, ChronoUnit.MILLIS);

        String refreshToken=Jwts.builder()
                .subject(userAuthRecord.userId())
                .claims()
                .add("sessionCode", userAuthRecord.sessionCode()) // 세션 코드 추가
                .issuedAt(Date.from(createdDate.atZone(ZoneId.systemDefault()).toInstant()))
                .expiration(Date.from(refreshExpirationDate.atZone(ZoneId.systemDefault()).toInstant()))
                .and() // 클레임 설정 종료
                .signWith(secretKeyHmac)
                .compact();

        return new JwtResponseRecord(null,refreshToken,createdDate,null,refreshExpirationDate);
    }

}
