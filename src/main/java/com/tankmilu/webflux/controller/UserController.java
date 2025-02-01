package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.JwtResponseRecord;
import com.tankmilu.webflux.record.LoginRequestRecord;
import com.tankmilu.webflux.record.UserRegRequests;
import com.tankmilu.webflux.record.UserRegResponse;
import com.tankmilu.webflux.security.JwtProvider;
import com.tankmilu.webflux.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${app.user.urls.base}")
@RequiredArgsConstructor
public class UserController {

    private final ReactiveAuthenticationManager authenticationManager;

    private final UserService userService;

    @PostMapping("/login")
    public Mono<ResponseEntity<JwtResponseRecord>> login(@RequestBody Mono<LoginRequestRecord> loginRequest) {
        return loginRequest.flatMap(request ->
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                        request.userId(),
                                        request.password()
                                )
                        )
                        .flatMap(authentication ->
                                userService.createToken(authentication)
                                        .map(ResponseEntity::ok)
                        )
        );
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
