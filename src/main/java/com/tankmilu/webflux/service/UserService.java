package com.tankmilu.webflux.service;

import com.tankmilu.webflux.entity.JwtRefreshTokenEntity;
import com.tankmilu.webflux.record.JwtResponseRecord;
import com.tankmilu.webflux.record.UserAuthRecord;
import com.tankmilu.webflux.repository.JwtRefreshTokenRepository;
import com.tankmilu.webflux.repository.UserRepository;
import com.tankmilu.webflux.security.JwtProvider;
import com.tankmilu.webflux.security.JwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;

    private final JwtProvider jwtProvider;

    private final JwtValidator jwtValidator;

    @Transactional
    public Mono<JwtResponseRecord> createToken(Authentication authentication) {
        String userId = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        // DB에서 사용자 플랜 조회
        return userRepository.findByUserId(userId)
                .flatMap(user -> {

                    String subscriptionPlan = user.getSubscriptionPlan();

                    UserAuthRecord userAuthRecord = new UserAuthRecord(
                            userId,
                            authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()),
                            user.getSubscriptionPlan(),
                            UUID.randomUUID().toString());

                    // Access 토큰 발행
                    JwtResponseRecord accessTokenRecord=jwtProvider.createAccessToken(userAuthRecord);
                    // Refresh 토큰 발행
                    JwtResponseRecord refreshTokenRecord=jwtProvider.createRefreshToken(userAuthRecord);
                    // Refresh 토큰 엔티티 생성
                    JwtRefreshTokenEntity jwtRefreshTokenEntity =
                            JwtRefreshTokenEntity
                                    .builder()
                                    .sessionCode(userAuthRecord.sessionCode())
                                    .userId(userId)
                                    .issuedAt(refreshTokenRecord.createdDate())
                                    .expiredAt(refreshTokenRecord.refreshExpirationDate())
                                    .build();


                    // 리프레시 토큰 저장
                    return jwtRefreshTokenRepository.save(jwtRefreshTokenEntity)
                            .map(saved -> new JwtResponseRecord(
                                    accessTokenRecord.accessToken(),
                                    refreshTokenRecord.refreshToken(),
                                    accessTokenRecord.createdDate(),
                                    accessTokenRecord.accessExpirationDate(),
                                    refreshTokenRecord.refreshExpirationDate()
                            ));
                });
    }

    @Transactional
    public Mono<JwtResponseRecord> accessTokenReissue(Authentication authentication, JwtResponseRecord jwtResponseRecord) {
        if (!jwtValidator.validateToken(jwtResponseRecord.refreshToken())) {
            throw new RuntimeException("RefreshToken 이 유효하지 않습니다.");
        }
        String userId = jwtValidator.extractUserId(jwtResponseRecord.refreshToken());
        String sessionCode = jwtValidator.extractSessionCode(jwtResponseRecord.refreshToken());
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        return jwtRefreshTokenRepository.findById(sessionCode)
                .switchIfEmpty(Mono.error(new RuntimeException("존재하지 않는 세션입니다.")))
                .flatMap(jwtRefreshTokenEntity -> jwtRefreshTokenRepository.delete(jwtRefreshTokenEntity)
                        .then(userRepository.findByUserId(userId)) // 최신 사용자 정보 다시 조회
                        .flatMap(user -> {
                            String newSessionCode = UUID.randomUUID().toString();
                            UserAuthRecord userAuthRecord = new UserAuthRecord(
                                    userId,
                                    authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()),
                                    user.getSubscriptionPlan(),
                                    UUID.randomUUID().toString());

                            // 새로운 Access, Refresh 토큰 생성
                            JwtResponseRecord newAccessToken = jwtProvider.createAccessToken(userAuthRecord);
                            JwtResponseRecord newRefreshToken = jwtProvider.createRefreshToken(userAuthRecord);

                            // 새로운 Refresh 엔티티 생성
                            JwtRefreshTokenEntity newEntity = JwtRefreshTokenEntity.builder()
                                    .sessionCode(newSessionCode)
                                    .userId(userId)
                                    .issuedAt(newRefreshToken.createdDate())
                                    .expiredAt(newRefreshToken.refreshExpirationDate())
                                    .build();

                            return jwtRefreshTokenRepository.save(newEntity)
                                    .map(saved -> new JwtResponseRecord(
                                            newAccessToken.accessToken(),
                                            newRefreshToken.refreshToken(),
                                            newAccessToken.createdDate(),
                                            newAccessToken.accessExpirationDate(),
                                            newRefreshToken.refreshExpirationDate()
                                    ));
                        })
                )
                .onErrorResume(e -> Mono.error(new RuntimeException("토큰 재발급 실패: " + e.getMessage())));
    }

}
