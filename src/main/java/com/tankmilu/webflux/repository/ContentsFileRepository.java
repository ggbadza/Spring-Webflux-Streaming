package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.ContentsFileEntity;
import com.tankmilu.webflux.entity.UserAuthorityEntity;
import com.tankmilu.webflux.record.FileInfoRecord;
import com.tankmilu.webflux.record.FileInfoSummaryResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ContentsFileRepository extends R2dbcRepository<ContentsFileEntity, Long> {

    Flux<ContentsFileEntity> findByContentsIdOrderByFileName(Long contentsId);

    @Query("""
        SELECT
            a.file_id  as id,
            a.file_name as file_name,
            a.file_path as file_path,
            a.contents_id as contents_id,
            a.subtitle_path as subtitle_path,
            a.resolution as resolution,
            a.created_at as createdAt,
            COALESCE(anime.folder_path, drama.folder_path, movie.folder_path) AS folder_path,
            COALESCE(anime.subscription_code, drama.subscription_code, movie.subscription_code) AS subscription_code
        FROM webflux.contents_file_entity a
        INNER JOIN webflux.contents_object_entity b ON a.contents_id = b.contents_id
        LEFT JOIN webflux.animation_folder_tree_entity anime ON b.type = 'anime' AND b.folder_id = anime.folder_id
        LEFT JOIN webflux.drama_folder_tree_entity drama ON b.type = 'drama' AND b.folder_id = drama.folder_id
        LEFT JOIN webflux.movie_folder_tree_entity movie ON b.type = 'movie' AND b.folder_id = movie.folder_id
        WHERE a.file_id = :fileId
        """)
    Mono<FileInfoRecord> findFileWithContentInfo(Long fileId);

    @Query("""
        SELECT b.*
        FROM webflux.contents_file_entity a
        INNER JOIN webflux.contents_file_entity b ON b.contents_id = a.contents_id
        WHERE a.file_id = :fileId
        """)
    Flux<ContentsFileEntity> findAllFilesSharingSameContentAsFileId(Long fileId);


    /**
     * folder_tree 테이블에서 file_id의 최댓값을 조회합니다.
     * @return Mono<Long> 최댓값. 결과가 없으면 empty Mono를 반환합니다.
     */
    @Query("SELECT MAX(file_id) FROM contents_file_entity")
    Mono<Long> findMaxFileId();

}
