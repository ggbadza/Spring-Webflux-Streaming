package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.JwtResponseRecord;
import com.tankmilu.webflux.record.LoginRequestRecord;
import com.tankmilu.webflux.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final ReactiveAuthenticationManager authenticationManager;

    private final JwtProvider jwtProvider;

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
                                jwtProvider.createToken(authentication)
                                        .map(ResponseEntity::ok)
                        )
        );
    }
}
