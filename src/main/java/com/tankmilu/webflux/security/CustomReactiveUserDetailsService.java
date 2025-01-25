package com.tankmilu.webflux.security;

import com.tankmilu.webflux.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomReactiveUserDetailsService implements ReactiveUserDetailsService {

    private final UserRepository userRepository; // R2DBC 기반 사용자 저장소

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        // 데이터베이스에서 사용자 조회
        return userRepository.findByUserId(username)
                .map(user -> new CustomUserDetails(
                        user.getUsername(),
                        user.getPassword(),
                        user.getAuthorities().stream()
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList()), // 권한 정보
                        user.getSubscriptionPlan() // 구독 플랜
                ));
    }
}
