package com.tankmilu.webflux.service;

import com.tankmilu.webflux.entity.JwtRefreshTokenEntity;
import com.tankmilu.webflux.entity.UserEntity;
import com.tankmilu.webflux.record.JwtResponseRecord;
import com.tankmilu.webflux.record.UserRegRequests;
import com.tankmilu.webflux.repository.JwtRefreshTokenRepository;
import com.tankmilu.webflux.repository.UserRepository;
import com.tankmilu.webflux.security.JwtValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtRefreshTokenRepository jwtRefreshTokenRepository;

    @Autowired
    private JwtValidator jwtValidator;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ReactiveAuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() {
        // 각 테스트 전 데이터 초기화
        userRepository.deleteAll().block();
        jwtRefreshTokenRepository.deleteAll().block();
    }

    @Test
    void registerTest_IdExists() {
        UserRegRequests request = new UserRegRequests("existingUser", "password", "userName", "test");
        userService.register(request).block(); // 기존 사용자 등록

        // 실행 & 검증
        StepVerifier.create(userService.register(request))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("동일한 ID가 존재합니다."))
                .verify();
    }

    @Test
    void registerTest_IdNotExists() {
        UserRegRequests request = new UserRegRequests("newUser", "password", "userName", "test");

        // 실행 & 검증
        StepVerifier.create(userService.register(request))
                .assertNext(response -> {
                    assertEquals(request.userId(), response.userId());
                    assertEquals(request.userName(), response.userName());
                    assertEquals(request.subscriptionPlan(), response.subscriptionPlan());
                    assertEquals("회원가입에 성공하였습니다.", response.msg());
                })
                .verifyComplete();
    }

    @Test
    void createTokenTest_WithValidAuthentication() {
        String userId = "testUser";
        String password = "password";
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password(passwordEncoder.encode(password))
                .userName("Test User")
                .subscriptionPlan("test")
                .build();
        userRepository.save(user).block();

        Authentication authentication = new UsernamePasswordAuthenticationToken(userId, password);

        StepVerifier.create(userService.createToken(authentication))
                .assertNext(jwtResponse -> {
                    assertNotNull(jwtResponse.accessToken());
                    assertNotNull(jwtResponse.refreshToken());
                    assertTrue(jwtResponse.accessExpirationDate().isAfter(jwtResponse.createdDate()));
                    assertTrue(jwtResponse.refreshExpirationDate().isAfter(jwtResponse.createdDate()));
                })
                .verifyComplete();
    }

    @Test
    void createTokenTest_WithInvalidAuthentication() {
        String userId = "testUser";
        String correctPassword  = "password1";
        String wrongPassword = "password2";
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password(passwordEncoder.encode(correctPassword))
                .userName("Test User")
                .subscriptionPlan("test")
                .build();
        userRepository.save(user).block();

        // 잘못된 패스워드로 인증 시도
        Authentication authentication = new UsernamePasswordAuthenticationToken(userId, wrongPassword);

        StepVerifier.create(authenticationManager.authenticate(authentication))
                .expectErrorMatches(throwable -> throwable instanceof BadCredentialsException)
                .verify();

    }

    @Test
    void accessTokenReissueTest_WithValidAuthentication() {
        // 준비
        String userId = "testUser";
        String password = "password";
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password(passwordEncoder.encode(password))
                .userName("Test User")
                .subscriptionPlan("test")
                .build();
        userRepository.save(user).block();

        Authentication authentication = new UsernamePasswordAuthenticationToken(userId, password);

        // 기존 토큰 검증
        JwtResponseRecord originalTokens = userService.createToken(authentication).block();
        assertNotNull(originalTokens);

        // 신규 토큰 발행 및 검증
        StepVerifier.create(
                        userService.accessTokenReissue(authentication, originalTokens)
                                .flatMap(newTokens -> {
                                    // 기존 토큰과 다름 검증
                                    assertNotEquals(originalTokens.accessToken(), newTokens.accessToken());
                                    assertNotEquals(originalTokens.refreshToken(), newTokens.refreshToken());

                                    // DB에 조회 요청
                                    String newSessionCode = jwtValidator.extractSessionCode(newTokens.refreshToken());
                                    return jwtRefreshTokenRepository.findById(newSessionCode)
                                            .doOnNext(tokenEntity -> {
                                                assertNotNull(tokenEntity);
                                                assertEquals(newSessionCode, tokenEntity.getId());
                                            });
                                })
                                // 기존 refresh 토큰이 삭제되었는지 확인
                                .flatMap(newTokens -> {
                                    String oldSessionCode = jwtValidator.extractSessionCode(originalTokens.refreshToken());
                                    return jwtRefreshTokenRepository.findById(oldSessionCode)
                                            // 만약 레코드가 남아있다면 실패해야 하므로:
                                            .doOnNext(entity -> fail("기존 토큰이 삭제되지 않고 남아있습니다. : " + entity));
                                })
                )
                .verifyComplete();
    }



}