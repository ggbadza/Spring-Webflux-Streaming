package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.ContentsObjectEntity;
import io.r2dbc.spi.Result;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ContentsObjectRepository extends R2dbcRepository<ContentsObjectEntity, Long> {

    @Query("""
            WITH RECURSIVE folder_hierarchy AS (
            -- 베이스 쿼리: 타입에 따른 초기 폴더 선택
            SELECT folder_id, name, folder_path, parent_folder_id, subscription_code, created_at, modified_at, has_files, contents_id
            FROM webflux.animation_folder_tree_entity
            WHERE 'anime' = :type and folder_id = :folderId
            UNION ALL
            SELECT folder_id, name, folder_path, parent_folder_id, subscription_code, created_at, modified_at, has_files, contents_id
            FROM webflux.movie_folder_tree_entity
            WHERE 'movie' = :type and folder_id = :folderId
            UNION ALL
            SELECT folder_id, name, folder_path, parent_folder_id, subscription_code, created_at, modified_at, has_files, contents_id
            FROM webflux.drama_folder_tree_entity
            WHERE 'drama' = :type and folder_id = :folderId
            UNION ALL
            -- 재귀 부분: 부모 폴더ID를 기준으로 자식 폴더 찾기
            SELECT c.folder_id, c.name, c.folder_path, c.parent_folder_id, c.subscription_code,
                   c.created_at, c.modified_at, c.has_files, c.contents_id
            FROM folder_hierarchy h
            JOIN (
                SELECT * FROM webflux.animation_folder_tree_entity WHERE 'anime' = :type
                UNION ALL
                SELECT * FROM webflux.movie_folder_tree_entity WHERE 'movie' = :type
                UNION ALL
                SELECT * FROM webflux.drama_folder_tree_entity WHERE 'drama' = :type
            ) c ON h.folder_id = c.parent_folder_id
        )
        
        SELECT b.contents_id, b.title, b.description, b.`type`, b.folder_id, b.release_ym, b.thumbnail_url, b.created_at, b.modified_at, b.poster_url
        FROM folder_hierarchy a, contents_object_entity b
        WHERE a.has_files = 1
        and b.`type`  = :type
        and b.folder_id = a.folder_id
        order by b.modified_at desc ,b.title
        """)
    Flux<ContentsObjectEntity> findContentsObjectEntitiesByTypeAndFolderIdRecursive(String type,Long folderId);

    Flux<ContentsObjectEntity> findTop20ByOrderByModifiedAtDesc();

    // 타입 필터링
    Flux<ContentsObjectEntity> findTop20ByTypeEqualsOrderByModifiedAtDesc(String type);


}
