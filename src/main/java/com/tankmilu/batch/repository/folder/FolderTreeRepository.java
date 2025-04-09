package com.tankmilu.batch.repository.folder;

import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@NoRepositoryBean
public interface FolderTreeRepository<T extends FolderTreeEntity> extends JpaRepository<T, Long> {


    @Modifying
    @Transactional
    void deleteByIdIn(Collection<Long> ids);

}
