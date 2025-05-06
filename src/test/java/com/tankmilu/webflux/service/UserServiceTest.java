package com.tankmilu.webflux.service;

import com.tankmilu.webflux.entity.UserEntity;
import com.tankmilu.webflux.record.UserRegRequest;
import com.tankmilu.webflux.repository.JwtRefreshTokenRepository;
import com.tankmilu.webflux.repository.UserRepository;
import com.tankmilu.webflux.security.JwtValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private TransactionalOperator transactionalOperator;

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

//    @BeforeEach
//    void setUp() {
//        transactionalOperator.execute(status ->
//                userRepository.deleteAll()
//                        .then(jwtRefreshTokenRepository.deleteAll())
//        ).blockLast();
//    }


    @Test
    void registerTest_IdExists() {
        transactionalOperator.execute(status -> {
                    UserRegRequest request = new UserRegRequest("existingUser", "userName", "password", "test");

                    return userService.register(request)
                            .then(userService.register(request)); // 중복 등록 시도
                })
                .as(StepVerifier::create)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("동일한 ID가 존재합니다."))
                .verify();
    }

    @Test
    void registerTest_IdNotExists() {
        UserRegRequest request = new UserRegRequest("newUser", "userName", "password", "test");

        StepVerifier.create(
                transactionalOperator.execute(status ->
                        userService.register(request)
                                .doOnNext(response -> {
                                    assertEquals(request.userId(), response.userId());
                                    assertEquals(request.userName(), response.userName());
                                    assertEquals(request.subscriptionPlan(), response.subscriptionPlan());
                                    assertEquals("회원가입에 성공하였습니다.", response.msg());

                                    // 트랜잭션 롤백 적용
                                    status.setRollbackOnly();
                                })
                                // 롤백 적용 후 트랜잭션을 중단
                                .then(Mono.empty())
                )
        ).verifyComplete();

        // 트랜잭션 롤백 확인 (데이터가 없어야 함)
        StepVerifier.create(userRepository.findById("newUser"))
                .expectNextCount(0) // 🔥 데이터가 없어야 성공
                .verifyComplete();
    }

    @Test
    void createTokenTest_WithValidAuthentication() {
        String userId = "testUser";
        String password = "password";

        // 비동기 과정 중에 토큰값 저장
        AtomicReference<String> refreshTokenRef = new AtomicReference<>();

        StepVerifier.create(
                transactionalOperator.execute(status ->
                        Mono.defer(() -> {
                                    UserEntity user = UserEntity.builder()
                                            .userId(userId)
                                            .password(passwordEncoder.encode(password))
                                            .userName("Test User")
                                            .subscriptionCode("100")
                                            .build();
                                    return userRepository.save(user)
                                            .then(userService.createToken(new UsernamePasswordAuthenticationToken(userId, password)));
                                })
                                .doOnNext(jwtResponse -> {
                                    assertNotNull(jwtResponse.accessToken());
                                    assertNotNull(jwtResponse.refreshToken());
                                    assertTrue(jwtResponse.accessExpirationDate().isAfter(jwtResponse.createdDate()));
                                    assertTrue(jwtResponse.refreshExpirationDate().isAfter(jwtResponse.createdDate()));

                                    // refreshToken 값을 저장
                                    refreshTokenRef.set(jwtValidator.extractSessionCode(jwtResponse.refreshToken()));

                                    status.setRollbackOnly();
                                })
                                .then(Mono.empty())
                )
        ).verifyComplete();

        // 트랜잭션 롤백 확인 (데이터가 없어야 함)
        StepVerifier.create(userRepository.findById("testUser"))
                .expectNextCount(0)
                .verifyComplete();

        StepVerifier.create(jwtRefreshTokenRepository.findById(refreshTokenRef.get()))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void createTokenTest_WithInvalidAuthentication() {
        String userId = "testUser";
        String correctPassword  = "password1";
        String wrongPassword = "password2";

        StepVerifier.create(
                        transactionalOperator.execute(status ->
                                Mono.defer(() -> {
                                            UserEntity user = UserEntity.builder()
                                                    .userId(userId)
                                                    .password(passwordEncoder.encode(correctPassword))
                                                    .userName("Test User")
                                                    .subscriptionCode("100")
                                                    .build();
                                            return userRepository.save(user);
                                        })
                                        .then(Mono.defer(() -> {
                                            // 잘못된 패스워드로 인증 시도
                                            Authentication authentication = new UsernamePasswordAuthenticationToken(userId, wrongPassword);
                                            return authenticationManager.authenticate(authentication);
                                        }))
                                        .doOnError(throwable -> {
                                            // 인증 실패로 인해 BadCredentialsException 발생 예상
                                            assertTrue(throwable instanceof BadCredentialsException);
                                            // 트랜잭션 롤백 설정
                                            status.setRollbackOnly();
                                        })
                        )
                ).expectErrorMatches(throwable -> throwable instanceof BadCredentialsException)
                .verify();

        // 트랜잭션 롤백 확인
        StepVerifier.create(userRepository.findById(userId))
                .expectNextCount(0)
                .verifyComplete();
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
                .subscriptionCode("100")
                .build();

        AtomicReference<String> oldRefreshTokenRef = new AtomicReference<>();
        AtomicReference<String> newRefreshTokenRef = new AtomicReference<>();

        StepVerifier.create(
                transactionalOperator.execute(status ->
                        userRepository.save(user)
                                .then(Mono.defer(() -> {
                                    Authentication authentication = new UsernamePasswordAuthenticationToken(userId, password);

                                    // 기존 토큰 생성 및 저장
                                    return userService.createToken(authentication)
                                            .flatMap(originalTokens -> {
                                                assertNotNull(originalTokens);

                                                // 기존 refresh 토큰 저장
                                                oldRefreshTokenRef.set(originalTokens.refreshToken());

                                                // 신규 토큰 발행 및 검증
                                                return userService.accessTokenReissue(authentication, originalTokens.refreshToken())
                                                        .flatMap(newTokens -> {
                                                            assertNotNull(newTokens);

                                                            // 새로운 refresh 토큰 저장
                                                            newRefreshTokenRef.set(newTokens.refreshToken());

                                                            // 기존 토큰과 다른지 검증
                                                            assertNotEquals(originalTokens.accessToken(), newTokens.accessToken());
                                                            assertNotEquals(originalTokens.refreshToken(), newTokens.refreshToken());

                                                            // DB에 새로운 refresh 토큰이 저장되었는지 확인
                                                            String newSessionCode = jwtValidator.extractSessionCode(newTokens.refreshToken());
                                                            return jwtRefreshTokenRepository.findById(newSessionCode)
                                                                    .doOnNext(tokenEntity -> {
                                                                        assertNotNull(tokenEntity);
                                                                        assertEquals(newSessionCode, tokenEntity.getId());
                                                                    })
                                                                    .thenReturn(newTokens);
                                                        })
                                                        // 기존 refresh 토큰이 삭제되었는지 확인
                                                        .flatMap(newTokens -> {
                                                            String oldSessionCode = jwtValidator.extractSessionCode(oldRefreshTokenRef.get());
                                                            return jwtRefreshTokenRepository.findById(oldSessionCode)
                                                                    .doOnNext(entity -> fail("기존 토큰이 삭제되지 않고 남아있습니다. : " + entity))
                                                                    .then();
                                                        });
                                            });
                                }))
                                .doOnSuccess(unused -> {
                                    status.setRollbackOnly();
                                })
                                .then(Mono.empty())
                )
        ).verifyComplete();

        // 트랜잭션 롤백 확인
        StepVerifier.create(userRepository.findById(userId))
                .expectNextCount(0) // 🔥 데이터가 없어야 성공
                .verifyComplete();

        StepVerifier.create(jwtRefreshTokenRepository.findById(jwtValidator.extractSessionCode(newRefreshTokenRef.get())))
                .expectNextCount(0) // 🔥 데이터가 없어야 성공 (롤백되었으므로 없음)
                .verifyComplete();

        StepVerifier.create(jwtRefreshTokenRepository.findById(jwtValidator.extractSessionCode(oldRefreshTokenRef.get())))
                .expectNextCount(0) // 🔥 데이터가 없어야 성공 (롤백되었으므로 없음)
                .verifyComplete();
    }




}