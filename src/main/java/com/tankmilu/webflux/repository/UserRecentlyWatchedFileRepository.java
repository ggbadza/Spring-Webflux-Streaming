package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.UserRecentlyWatchedFileEntity;
import com.tankmilu.webflux.record.VideoInfoSummaryResponse;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRecentlyWatchedFileRepository extends R2dbcRepository<UserRecentlyWatchedFileEntity, Long> {

    @Query("""
        SELECT
            cf.file_id AS id,
            cf.file_name AS file_name,
            cf.contents_id AS contents_id,
            CASE
                WHEN cf.subtitle_path IS NOT NULL AND cf.subtitle_path <> '' THEN 'true'
                ELSE 'false'
            END AS has_subtitle,
            cf.resolution AS resolution,
            cf.created_at AS created_at,
            cf.thumbnail_url AS thumbnail_url,
            co.title AS title,
            urwf.progress AS progress,
            urwf.watched_at AS watched_at
        FROM user_recently_watched_file urwf
        INNER JOIN contents_file_entity cf ON cf.file_id = urwf.file_id
        LEFT JOIN contents_object_entity co ON co.contents_id = cf.contents_id
        WHERE urwf.user_id = :userId
        ORDER BY urwf.watched_at DESC
        LIMIT :size
        """)
    Flux<VideoInfoSummaryResponse> findVideoHistoryByUserId(String userId, int size);

    @Modifying
    @Query("""
        INSERT INTO user_recently_watched_file (
            user_id,
            file_id,
            position_sec,
            duration_sec,
            progress,
            watched_at
        )
        VALUES (
            :userId,
            :fileId,
            :positionSec,
            :durationSec,
            :progress,
            NOW()
        )
        ON DUPLICATE KEY UPDATE
            position_sec = VALUES(position_sec),
            duration_sec = VALUES(duration_sec),
            progress = VALUES(progress),
            watched_at = VALUES(watched_at)
        """)
    Mono<Integer> setVideoRecord(
            @Param("userId") String userId,
            @Param("fileId") Long fileId,
            @Param("positionSec") Integer positionSec,
            @Param("durationSec") Integer durationSec,
            @Param("progress") Integer progress);

    Mono<UserRecentlyWatchedFileEntity> findByUserIdAndFileId(String userId, Long fileId);
}
