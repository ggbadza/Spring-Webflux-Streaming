package com.tankmilu.webflux.repository.folder;

import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.NoRepositoryBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

// 상속용 리파지토리 인터페이스
@NoRepositoryBean
public interface FolderTreeRepository<T extends FolderTreeEntity> extends R2dbcRepository<T, Long> {

    Mono<T> findByFolderId(Long folderId);

    Flux<T> findByParentFolderId(Long parentFolderId);

    Flux<T> saveAll(List<T> childFolders);
}
