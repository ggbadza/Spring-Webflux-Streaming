package com.tankmilu.webflux.security;

import com.tankmilu.webflux.entity.UserAuthorityEntity;
import com.tankmilu.webflux.repository.UserAuthorityRepository;
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

    private final UserRepository userRepository;

    private final UserAuthorityRepository userAuthorityRepository;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        // 데이터베이스에서 사용자 조회
        return userRepository.findByUserId(username)
                .zipWhen(user -> userAuthorityRepository.findByUserId(user.getUserId()).collectList())
                .map(tuple -> {
                    // tuple 은 (UserEntity, List<UserAuthorityEntity>) 로 구성
                    var user = tuple.getT1();                   // UserEntity
                    var authorityEntities = tuple.getT2();      // List<UserAuthorityEntity>

                    // 권한 정보가 없으면 빈 리스트가 들어옴
                    var grantedAuthorities = authorityEntities.stream()
                            .map(UserAuthorityEntity::getAuthority)  
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

                    // UserDetails 구현체 생성 (username, password, 권한 목록, 기타 정보 등)
                    return new CustomUserDetails(
                            user.getUserId(),
                            user.getPassword(),
                            grantedAuthorities,             // List<GrantedAuthority>
                            user.getSubscriptionCode()
                    );
                });
    }
}
