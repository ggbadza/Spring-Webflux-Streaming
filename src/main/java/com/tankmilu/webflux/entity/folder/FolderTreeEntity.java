package com.tankmilu.webflux.entity.folder;

import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

// 상속용 엔티티 테이블
@Getter
public abstract class FolderTreeEntity implements Persistable<Long> {
    @Id
    @Column("folder_id")
    private Long folderId;  // AUTO_INCREMENT는 DB에서 처리

    @Column("name")
    private String name;

    @Column("folder_path")
    private String folderPath;

    @Column("parent_folder_id")
    private Long parentFolderId;

    @Column("subscription_code")
    private String subscriptionCode;  // 권한 속성

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("modified_at")
    private LocalDateTime modifiedAt;

    @Column("has_files")
    private Boolean hasFiles;

    @Column("contents_id")
    private Long contentsId;  // AUTO_INCREMENT는 DB에서 처리

    @Transient
    private boolean isNewRecord;

    @Override
    public Long getId() {
        return this.folderId;
    }

    @Override
    public boolean isNew() {
        return this.isNewRecord;
    }
}

