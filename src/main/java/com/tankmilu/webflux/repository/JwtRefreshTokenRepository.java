package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.JwtRefreshTokenEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface JwtRefreshTokenRepository extends ReactiveCrudRepository<JwtRefreshTokenEntity, String> {
}
