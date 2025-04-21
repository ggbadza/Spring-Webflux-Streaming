package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.ContentsFileEntity;
import com.tankmilu.webflux.entity.UserAuthorityEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface ContentsFileRepository extends R2dbcRepository<ContentsFileEntity, Long> {

    Flux<ContentsFileEntity> findByContentsIdOrderByFileName(Long contentsId);
}
