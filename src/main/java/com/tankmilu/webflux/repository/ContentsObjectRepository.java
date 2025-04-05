package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.ContentsObjectEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface ContentsObjectRepository extends R2dbcRepository<ContentsObjectEntity, Long> {
}
