package com.tankmilu.batch.repository.folder;

import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface FolderTreeRepository<T extends FolderTreeEntity> extends JpaRepository<T, Long> {
}
