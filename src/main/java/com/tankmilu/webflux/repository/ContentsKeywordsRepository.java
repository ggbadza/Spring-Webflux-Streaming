package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.ContentsKeywordsEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface ContentsKeywordsRepository extends R2dbcRepository<ContentsKeywordsEntity, Long> {

    Flux<ContentsKeywordsEntity> findBySeriesId(String seriesId);
}
