package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.ContentsObjectEntity;
import com.tankmilu.webflux.entity.ContentsQuarterEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface ContentsQuarterRepository extends R2dbcRepository<ContentsQuarterEntity, String> {

    @Query("""
            SELECT
                c.contents_id,
                c.title,
                c.description,
                c.thumbnail_url,
                COALESCE(q.poster_url, c.poster_url) AS poster_url,
                c.background_url,
                c.release_ym,
                c.type,
                c.folder_id,
                c.created_at,
                c.modified_at,
                c.subscription_code,
                c.series_id,
                c.season
            FROM webflux.contents_quarter_entity q
            JOIN webflux.contents_object_entity c ON c.contents_id = q.contents_id
            WHERE q.quarter_key = :quarterKey
            ORDER BY q.contents_id
            """)
    Flux<ContentsObjectEntity> findContentsByQuarterKey(String quarterKey);

    @Query("""
            SELECT DISTINCT quarter_key
            FROM webflux.contents_quarter_entity
            ORDER BY quarter_key DESC
            """)
    Flux<String> findDistinctQuarterKeys();
}
