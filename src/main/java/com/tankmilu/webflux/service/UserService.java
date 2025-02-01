package com.tankmilu.webflux.service;

import com.tankmilu.webflux.entity.JwtRefreshTokenEntity;
import com.tankmilu.webflux.entity.UserEntity;
import com.tankmilu.webflux.record.JwtResponseRecord;
import com.tankmilu.webflux.record.UserAuthRecord;
import com.tankmilu.webflux.record.UserRegRequests;
import com.tankmilu.webflux.record.UserRegResponse;
import com.tankmilu.webflux.repository.JwtRefreshTokenRepository;
import com.tankmilu.webflux.repository.UserRepository;
import com.tankmilu.webflux.security.JwtProvider;
import com.tankmilu.webflux.security.JwtValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;

    private final JwtProvider jwtProvider;

    private final JwtValidator jwtValidator;

    private final PasswordEncoder passwordEncoder;

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

    @Transactional
    public Mono<UserRegResponse> register(UserRegRequests userRegRequests) {
        return userRepository.findByUserId(userRegRequests.userId())
                // 이미 존재하는 사용자라면 에러 반환
                .flatMap(existingUser -> Mono.<UserEntity>error(new RuntimeException("동일한 ID가 존재합니다.")))
                // 사용자가 존재하지 않으면, 새 사용자 생성
                .switchIfEmpty(Mono.defer(() -> {
                    UserEntity newUser = UserEntity.builder()
                            .userId(userRegRequests.userId())
                            // 평문 비밀번호를 BCryptPasswordEncoder 등을 통해 해싱
                            .password(passwordEncoder.encode(userRegRequests.password()))
                            .userName(userRegRequests.userName())
                            .subscriptionPlan(userRegRequests.subscriptionPlan())
                            .isNewRecord(true)
                            .build();
                    return userRepository.save(newUser);
                }))
                .map(savedUser -> new UserRegResponse(
                        savedUser.getUserId(),
                        savedUser.getUserName(),
                        savedUser.getSubscriptionPlan(),
                        "회원가입에 성공하였습니다.")
                );
    }

}
