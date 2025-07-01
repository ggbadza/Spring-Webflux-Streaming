package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.FeaturedBannersEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface FeaturedBannersRepository extends R2dbcRepository<FeaturedBannersEntity, Long> {

    Flux<FeaturedBannersEntity> findAllByOrderBySequenceIdAsc();
}
