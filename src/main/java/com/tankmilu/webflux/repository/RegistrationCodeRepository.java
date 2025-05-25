package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.RegistrationCodeEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

public interface RegistrationCodeRepository extends R2dbcRepository<RegistrationCodeEntity, Long> {
    
    /**
     * 등록 코드로 조회
     */
    Mono<RegistrationCodeEntity> findByRegCode(String regCode);

    /**
     * 등록 코드 사용 횟수 증가
     */
    @Modifying
    @Query("""
        UPDATE registration_codes
        SET current_usage_count = current_usage_count + 1,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
        """)
    Mono<Void> incrementUsageCount(@Param("id") Long id);

}