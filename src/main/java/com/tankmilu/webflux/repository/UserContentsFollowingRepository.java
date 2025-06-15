package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.UserContentsFollowingEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserContentsFollowingRepository extends R2dbcRepository<UserContentsFollowingEntity, String> {

    @Query("SELECT MAX(ucf.followingSeq) FROM user_contents_following ucf WHERE ucf.userId = :userId")
    Mono<Integer> findMaxFollowingSeqByUserId(@Param("userId") String userId);

    Flux<UserContentsFollowingEntity> findByUserIdOrderByFollowingSeq(String userId);
}
