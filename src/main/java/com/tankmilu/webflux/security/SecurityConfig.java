package com.tankmilu.webflux.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {


    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    private final ReactiveUserDetailsService userDetailsService;

    @Value("${app.user.urls.base}")
    private String userUrl;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) throws Exception { // 웹플럭스 기반 필터체인 클래스
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .exceptionHandling(exceptionHandlingSpec -> exceptionHandlingSpec
                .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)) // 401 응답 반환
                )
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .addFilterAt(authenticationWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchange -> exchange
//                        .pathMatchers("/video/test").permitAll()   // 공개된 주소
//                        .pathMatchers("/h2-console").permitAll()
                        .pathMatchers(userUrl+"/**").permitAll()
                        .anyExchange().authenticated()
                )
                .build();
    }

    @Bean
    public AuthenticationWebFilter authenticationWebFilter() {
        AuthenticationWebFilter authenticationWebFilter = new AuthenticationWebFilter(jwtReactiveAuthenticationManager());
        authenticationWebFilter.setServerAuthenticationConverter(jwtAuthenticationConverter); // JWT 인증 컨버터 설정
        return authenticationWebFilter;
    }

    // 비밀번호 호출하는 오류 존재하여 jwt용으로 교체
//    @Bean
//    public ReactiveAuthenticationManager authenticationManager() {
//        UserDetailsRepositoryReactiveAuthenticationManager authManager = new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
//        authManager.setPasswordEncoder(this.passwordEncoder());
//        return authManager;
//    }

    @Bean
    public ReactiveAuthenticationManager jwtReactiveAuthenticationManager() {
        return authentication -> Mono.just(
                new UsernamePasswordAuthenticationToken(
                        authentication.getPrincipal(), // 사용자 정보
                        null, // 권한 증명 (jwt는 패스워드 인증이 불필요하므로 null)
                        authentication.getAuthorities() // 사용자 권한
                )
        );
    }

    @Value("${cors.allowedOrigins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() { // 웹플럭스 기반 Cors 설정 클래스
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins); // 허용할 도메인
        configuration.addAllowedMethod("*"); // 허용할 HTTP 메서드
        configuration.addAllowedHeader("*"); // 허용할 헤더
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
