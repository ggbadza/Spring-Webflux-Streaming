package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.UserContentsRecommendEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface UserContentsRecommendRepository extends R2dbcRepository<UserContentsRecommendEntity, UserContentsRecommendEntity.UserContentsRecommendId> {

    Flux<UserContentsRecommendEntity> findByIdUserIdOrderByIdRecommendSeq(String userId);
}
