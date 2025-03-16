package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.*;
import com.tankmilu.webflux.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("${app.user.urls.base}")
@RequiredArgsConstructor
public class UserController {

    private final ReactiveAuthenticationManager authenticationManager;

    private final UserService userService;

    @PostMapping("/login")
    public Mono<ResponseEntity<Boolean>> login(@RequestBody Mono<LoginRequestRecord> loginRequest) {
        return loginRequest.flatMap(request ->
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                        request.userId(),
                                        request.password()
                                )
                        )
                        .flatMap(authentication ->
                                userService.createToken(authentication)
                                        .map(jwtResponse -> {
                                            // 액세스 토큰 쿠키 생성
                                            ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", jwtResponse.accessToken())
                                                    .httpOnly(true)
                                                    .secure(true)
                                                    .path("/")
                                                    .maxAge(Duration.between(LocalDateTime.now(), jwtResponse.refreshExpirationDate()))  // 현재 시간과 만기 시간의 차이
                                                    .build();

                                            // 리프레시 토큰 쿠키 생성
                                            ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", jwtResponse.refreshToken())
                                                    .httpOnly(true)
                                                    .secure(true)
                                                    .path("/")
                                                    .maxAge(Duration.between(LocalDateTime.now(), jwtResponse.refreshExpirationDate())) // 현재 시간과 만기 시간의 차이
                                                    .build();

                                            // jwt 토큰을 Set-Cookie 헤더로 추가
                                            return ResponseEntity.ok()
                                                    .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                                                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                                                    .body(true);
                                        })
                        )
                        )
                        .onErrorResume(e -> {
                            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                    .body(false));
                        });
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<UserRegResponse>> register(@RequestBody Mono<UserRegRequests> registerRequest) {
        return registerRequest
                .flatMap(userRegRequests ->
                                userService.register(userRegRequests)
                                        .map(ResponseEntity::ok)
                )
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.badRequest()
                            .body(new UserRegResponse(null, null, null, "회원가입 실패: " + e.getMessage())));
                });
    }

}
