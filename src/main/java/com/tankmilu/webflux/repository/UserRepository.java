package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.UserEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends R2dbcRepository<UserEntity, String> {

    Mono<UserEntity> findByUserId(String userId);
}
