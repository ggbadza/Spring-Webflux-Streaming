package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.exception.DuplicateUserException;
import com.tankmilu.webflux.exception.InvalidUserRegCodeException;
import com.tankmilu.webflux.exception.UserException;
import com.tankmilu.webflux.record.*;
import com.tankmilu.webflux.security.CustomUserDetails;
import com.tankmilu.webflux.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
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

    /**
     * 사용자 로그인을 처리함
     * 
     * @param loginRequest 로그인 요청 정보 (userId, password)
     * @return 로그인 성공 여부와 JWT 토큰이 포함된 쿠키 반환
     */
    @PostMapping("${app.user.urls.login}")
    public Mono<ResponseEntity<Boolean>> login(@RequestBody LoginRequestRecord loginRequest) {
        return authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                loginRequest.userId(),
                                loginRequest.password()
                        )
                )
                .flatMap(authentication ->
                        userService.createToken(authentication,loginRequest.rememberMe())
                                .map(this::buildTokenResponse)
                );
    }


    /**
     * 액세스 토큰을 재발급함
     * 
     * @param exchange 서버 웹 교환 객체 (쿠키 정보 포함)
     * @return 토큰 재발급 성공 여부와 새로운 JWT 토큰이 포함된 쿠키 반환
     */
    @RequestMapping(value = "${app.user.urls.reissue}", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<Boolean>> reissue(ServerWebExchange exchange) {
        // 리프레시토큰 추출
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst("refreshToken");
        if (cookie == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(false));
        }
        String refreshToken = cookie.getValue();

        return userService.accessTokenReissue(refreshToken)
                .map(this::buildTokenResponse);
    }

    /**
     * JWT 토큰을 HTTP 응답 쿠키로 변환함
     * 
     * @param jwtResponse JWT 액세스 및 리프레시 토큰 정보
     * @return 토큰이 쿠키로 포함된 HTTP 응답 객체
     */
    private ResponseEntity<Boolean> buildTokenResponse(JwtAccessAndRefreshRecord jwtResponse) {
        if (jwtResponse != null) {
            // 액세스 토큰 쿠키 생성
            ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", jwtResponse.accessToken())
                    .httpOnly(true)
                    .secure(true) // SSL 통신 시 true 설정 필요
                    .path("/")
                    .maxAge(Duration.between(LocalDateTime.now(), jwtResponse.refreshExpirationDate()))  // 현재 시간과 만기 시간의 차이
                    .build();

            // 리프레시 토큰 쿠키 생성
            ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", jwtResponse.refreshToken())
                    .httpOnly(true)
                    .secure(true) // SSL 통신 시 true 설정 필요
                    .path("/")
                    .maxAge(Duration.between(LocalDateTime.now(), jwtResponse.refreshExpirationDate())) // 현재 시간과 만기 시간의 차이
                    .build();

            // jwt 토큰을 Set-Cookie 헤더로 추가
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                    .body(true);
        } else {
            // 액세스 토큰 쿠키 생성
            ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", "")
                    .httpOnly(true)
                    .secure(true) // SSL 통신 시 true 설정 필요
                    .path("/")
                    .maxAge(Duration.ZERO)  // 폐기용이므로 0
                    .build();

            // 리프레시 토큰 쿠키 생성
            ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken","")
                    .httpOnly(true)
                    .secure(true) // SSL 통신 시 true 설정 필요
                    .path("/")
                    .maxAge(Duration.ZERO) // 폐기용이므로 0
                    .build();

            // jwt 토큰을 Set-Cookie 헤더로 추가
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                    .body(true);
        }
    }

    /**
     * 신규 사용자 등록을 처리함
     * 
     * @param registerRequest 사용자 등록 요청 정보
     * @return 등록 결과 정보 반환
     */
    @PostMapping("${app.user.urls.register}")
    public Mono<UserRegResponse> register(@RequestBody UserRegRequest registerRequest) {
        return userService.register(registerRequest);
    }


    /**
     * 현재 인증된 사용자의 정보를 조회함
     * 
     * @param userDetails 인증된 사용자 상세 정보
     * @return 사용자 프로필 정보 반환
     */
    @RequestMapping(value = "${app.user.urls.me}", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<UserRegResponse> aboutMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return userService.aboutMe(userDetails);
    }

    /**
     * 현재 인증된 사용자의 정보를 초기화
     *
     * @param exchange 인증된 사용자 토큰 정보
     * @return
     */
    @RequestMapping(value = "${app.user.urls.logout}", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<Boolean>> logout(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst("refreshToken");
        if (cookie == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.OK).body(true));
        }
        String refreshToken = cookie.getValue();
        return userService.revokeToken(refreshToken)
                .thenReturn(buildTokenResponse(null)) // Mono<Void>가 완료되면 ResponseEntity<Boolean>을 방출
                .onErrorResume(e -> {
                    System.err.println("토큰 폐기중에 에러 발생 : " + e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.OK).body(true)); // 에러가 나도 클라이언트에겐 성공처럼 보낼 경우
                    // return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(false)); // 에러 응답을 보내고 싶을 경우
                });
    }


}
