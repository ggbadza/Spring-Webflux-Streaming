package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.ContentsFileEntity;
import com.tankmilu.webflux.entity.UserAuthorityEntity;
import com.tankmilu.webflux.record.FileInfoRecord;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ContentsFileRepository extends R2dbcRepository<ContentsFileEntity, Long> {

    Flux<ContentsFileEntity> findByContentsIdOrderByFileName(Long contentsId);

    @Query("""
        SELECT a.id as id, a.file_name as fileName, a.file_path as filePath, a.contents_id as contentsId,
               a.subtitle_path as subtitlePath, a.resolution as resolution, a.created_at as createdAt,
               COALESCE(anime.folder_path, drama.folder_path, movie.folder_path) AS folderPath,
               COALESCE(anime.subscription_code, drama.subscription_code, movie.subscription_code) AS subscriptionCode,
        FROM contents_file_entity a
        INNER JOIN contents_object_entity b ON a.contents_id = b.id
        LEFT JOIN animation_folder_tree_entity anime ON b.type = 'anime' AND b.folder_id = anime.folder_id
        LEFT JOIN drama_folder_tree_entity drama ON b.type = 'drama' AND b.folder_id = drama.folder_id
        LEFT JOIN movie_folder_tree_entity movie ON b.type = 'movie' AND b.folder_id = movie.folder_id
        WHERE a.id = :fileId
        """)
    Mono<FileInfoRecord> findFileWithContentInfo(Long fileId);
}
