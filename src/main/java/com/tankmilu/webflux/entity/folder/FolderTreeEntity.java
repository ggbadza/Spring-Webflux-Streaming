package com.tankmilu.webflux.entity.folder;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class FolderTreeEntity implements Persistable<Long> {
    @Id
    @Column("folder_id")
    private Long folderId;  // AUTO_INCREMENT는 DB에서 처리

    @Column("name")
    private String name;

    @Setter
    @Column("folder_path")
    private String folderPath;

    @Setter
    @Column("parent_folder_id")
    private Long parentFolderId;

    @Column("subscription_code")
    private String subscriptionCode;  // 권한 속성

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @Setter
    @Column("modified_at")
    private LocalDateTime modifiedAt;

    @Setter
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

    @Transient
    @Setter
    private String changeCd;

}

