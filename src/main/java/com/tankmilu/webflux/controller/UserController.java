package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.*;
import com.tankmilu.webflux.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
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
                                        .map(this::buildTokenResponse)
                        )
                )
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(false)));
    }

    @PostMapping("/reissue")
    public Mono<ResponseEntity<Boolean>> reissue(ServerWebExchange exchange) {
        // 리프레시토큰 추출
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst("refreshToken");
        if (cookie == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(false));
        }
        String refreshToken = cookie.getValue();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> userService.accessTokenReissue(authentication, refreshToken))
                .map(this::buildTokenResponse)
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(false)));
    }

    // jwt를 헤더로 변환해서 응답
    private ResponseEntity<Boolean> buildTokenResponse(JwtAccessAndRefreshRecord jwtResponse){
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
