package com.tankmilu.webflux.repository;

import com.tankmilu.webflux.entity.FolderTreeEntity;
import com.tankmilu.webflux.entity.UserAuthorityEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FolderTreeRepository extends R2dbcRepository<FolderTreeEntity, Long> {

    Mono<FolderTreeEntity> findByFolderId(Long folderId);

    Flux<FolderTreeEntity> findByParentFolderId(Long parentFolderId);
}
