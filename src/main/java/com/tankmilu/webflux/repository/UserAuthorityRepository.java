package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.UserAuthorityEntity;
import com.tankmilu.webflux.entity.UserEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserAuthorityRepository extends R2dbcRepository<UserAuthorityEntity, String> {
    Flux<UserAuthorityEntity> findByUserId(String userId);
}
