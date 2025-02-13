package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.JwtRefreshTokenEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface JwtRefreshTokenRepository extends R2dbcRepository<JwtRefreshTokenEntity, String> {
}
