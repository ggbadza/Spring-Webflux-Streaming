package com.tankmilu.webflux.service;

import com.tankmilu.webflux.entity.JwtRefreshTokenEntity;
import com.tankmilu.webflux.entity.UserEntity;
import com.tankmilu.webflux.record.JwtAccessAndRefreshRecord;
import com.tankmilu.webflux.record.UserAuthRecord;
import com.tankmilu.webflux.record.UserRegRequest;
import com.tankmilu.webflux.record.UserRegResponse;
import com.tankmilu.webflux.repository.JwtRefreshTokenRepository;
import com.tankmilu.webflux.repository.RegistrationCodeRepository;
import com.tankmilu.webflux.repository.UserRepository;
import com.tankmilu.webflux.security.CustomUserDetails;
import com.tankmilu.webflux.security.JwtProvider;
import com.tankmilu.webflux.security.JwtValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;

    private final RegistrationCodeRepository registrationCodeRepository;

    private final JwtProvider jwtProvider;

    private final JwtValidator jwtValidator;

    private final PasswordEncoder passwordEncoder;

    private final TransactionalOperator transactionalOperator;

    public Mono<JwtAccessAndRefreshRecord> createToken(Authentication authentication) {
        String userId = authentication.getName();
        // 권한 목록 추출
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return userRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("사용자가 존재하지 않습니다.")))
                // 유저 정보를 UserAuthRecord로 변환 후 토큰 생성
                .map(user ->  {

                    UserAuthRecord userAuthRecord = new UserAuthRecord(
                            userId,
                            roles,
                            user.getSubscriptionCode(),
                            UUID.randomUUID().toString());
                    JwtAccessAndRefreshRecord accessToken = jwtProvider.createAccessToken(userAuthRecord);
                    JwtAccessAndRefreshRecord refreshToken = jwtProvider.createRefreshToken(userAuthRecord);
                    return Tuples.of(userAuthRecord, accessToken, refreshToken);
                })
                // Refresh 토큰 엔티티 생성, 다시 튜플로 묶어서 리턴
                .flatMap(tuple -> {
                    UserAuthRecord userAuthRecord = tuple.getT1();
                    JwtAccessAndRefreshRecord accessToken = tuple.getT2();
                    JwtAccessAndRefreshRecord refreshToken = tuple.getT3();

                    JwtRefreshTokenEntity entity = JwtRefreshTokenEntity.builder()
                            .sessionCode(userAuthRecord.sessionCode())
                            .userId(userAuthRecord.userId())
                            .issuedAt(refreshToken.createdDate())
                            .expiredAt(refreshToken.refreshExpirationDate())
                            .build();

                    return Mono.just(Tuples.of(accessToken, refreshToken, entity));
                })
                // 리프레시 토큰 엔티티 DB 저장
                .flatMap(tuple -> {
                    JwtAccessAndRefreshRecord accessToken = tuple.getT1();
                    JwtAccessAndRefreshRecord refreshToken = tuple.getT2();
                    JwtRefreshTokenEntity entity = tuple.getT3();

                    return jwtRefreshTokenRepository.save(entity)
                            .map(saved -> new JwtAccessAndRefreshRecord(
                                    accessToken.accessToken(),
                                    refreshToken.refreshToken(),
                                    accessToken.createdDate(),
                                    accessToken.accessExpirationDate(),
                                    refreshToken.refreshExpirationDate()
                            ));
                })
                // 에러 발생 시 메시지 변환
                .onErrorResume(e -> Mono.error(new Exception("리프레시 토큰 발급에 실패하였습니다.", e)));
    }

    public Mono<JwtAccessAndRefreshRecord> accessTokenReissue(Authentication authentication,
                                                              String refreshToken) {
        // RefreshToken 검사
        if (!jwtValidator.validateToken(refreshToken)) {
            return Mono.error(new RuntimeException("RefreshToken 이 유효하지 않습니다."));
        }
        // 토큰에서 사용자 정보 추출
        String userId = jwtValidator.extractUserId(refreshToken);
        String sessionCode = jwtValidator.extractSessionCode(refreshToken);
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        // 세션 코드로 RefreshTokenEntity 조회
        return jwtRefreshTokenRepository.findById(sessionCode)
                .switchIfEmpty(Mono.error(new RuntimeException("존재하지 않는 세션입니다.")))

                // 기존 RefreshTokenEntity 삭제
                .flatMap(refreshTokenEntity ->
                        jwtRefreshTokenRepository.delete(refreshTokenEntity)
                                // 삭제 후 다음 단계로 이동
                                .thenReturn(refreshTokenEntity) // Mono<refreshTokenEntity>
                )
                // 사용자 정보 조회
                .flatMap(deletedEntity ->
                        userRepository.findByUserId(userId)
                                .switchIfEmpty(Mono.error(new RuntimeException("존재하지 않는 사용자입니다.")))
                )
                // 새 토큰 생성 및 새 RefreshTokenEntity 저장
                .flatMap(user -> {
                    // 새로운 SessionCode 발급
                    String newSessionCode = UUID.randomUUID().toString();
                    // UserAuthRecord 생성
                    UserAuthRecord userAuthRecord = new UserAuthRecord(
                            userId,
                            authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()),
                            user.getSubscriptionCode(),
                            newSessionCode
                    );
                    // 새 AccessToken, RefreshToken 발행
                    JwtAccessAndRefreshRecord newAccessToken = jwtProvider.createAccessToken(userAuthRecord);
                    JwtAccessAndRefreshRecord newRefreshToken = jwtProvider.createRefreshToken(userAuthRecord);
                    // 새 RefreshTokenEntity 생성
                    JwtRefreshTokenEntity newEntity = JwtRefreshTokenEntity.builder()
                            .sessionCode(newSessionCode)
                            .userId(userId)
                            .issuedAt(newRefreshToken.createdDate())
                            .expiredAt(newRefreshToken.refreshExpirationDate())
                            .build();
                    // 새 엔티티 DB 저장 후 결과를 Mono로 반환
                    return jwtRefreshTokenRepository.save(newEntity)
                            .map(savedEntity ->
                                    new JwtAccessAndRefreshRecord(
                                            newAccessToken.accessToken(),
                                            newRefreshToken.refreshToken(),
                                            newAccessToken.createdDate(),
                                            newAccessToken.accessExpirationDate(),
                                            newRefreshToken.refreshExpirationDate()
                                    )
                            );
                })
                // 에러 메시지 일괄 처리
                .onErrorResume(e -> Mono.error(new RuntimeException("토큰 재발급 실패: " + e.getMessage(), e)));
    }

    public Mono<UserRegResponse> register(UserRegRequest userRegRequests) {
        return registrationCodeRepository
                // 1. 등록 코드 존재 여부 확인
                .findByRegCode(userRegRequests.regCode())
                .switchIfEmpty(Mono.error(new RuntimeException("등록 코드가 존재하지 않습니다.")))
                // 2. 등록 코드 유효성 검증
                .flatMap(code -> {
                    if (!code.isUsable()) {
                        return Mono.error(new RuntimeException("유효하지 않은 등록 코드입니다."));
                    }
                    return Mono.just(code);
                })
                // 3. 등록 코드 사용 횟수 증가
                .flatMap(validCode ->
                        registrationCodeRepository.incrementUsageCount(validCode.getId())
                                .thenReturn(validCode)
                )
                // 4. 사용자 중복 체크 및 회원가입 진행
                .flatMap(validCode -> userRepository.findByUserId(userRegRequests.userId())
                        // 이미 존재하는 사용자라면 에러 반환
                        .flatMap(existingUser -> Mono.<UserEntity>error(new RuntimeException("동일한 ID가 존재합니다.")))
                        // 사용자가 존재하지 않으면, 새 사용자 생성
                        .switchIfEmpty(Mono.defer(() -> {
                            UserEntity newUser = UserEntity.builder()
                                    .userId(userRegRequests.userId())
                                    .password(passwordEncoder.encode(userRegRequests.password()))
                                    .userName(userRegRequests.userName())
                                    .subscriptionCode(validCode.getSubscriptionCode()) // 등록 코드에서 가져온 값 사용
                                    .build();
                            return userRepository.save(newUser);
                        }))
                )
                .map(savedUser -> new UserRegResponse(
                        savedUser.getUserId(),
                        savedUser.getUserName(),
                        savedUser.getSubscriptionCode(),
                        "회원가입에 성공하였습니다.")
                )
                .as(transactionalOperator::transactional);
    }

    public Mono<UserRegResponse> aboutMe(CustomUserDetails userDetails){
        return Mono.justOrEmpty(userDetails)
                .flatMap(details -> userRepository.findByUserId(details.getUsername())
                        .map(user -> new UserRegResponse(
                                user.getUserId(),
                                user.getUserName(),
                                user.getSubscriptionCode(),
                                "정상적으로 처리되었습니다."
                        )))
                .switchIfEmpty(Mono.just(new UserRegResponse(null, null, null, "유저 세션이 존재하지 않습니다.")));
    }

}
