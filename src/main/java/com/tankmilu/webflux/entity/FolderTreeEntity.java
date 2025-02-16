package com.tankmilu.webflux.entity;

import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Table()
public class FolderTreeEntity  {
    @Id
    @Column("folder_id")
    private Long folderId;  // AUTO_INCREMENT는 DB에서 처리

    @Column("name")
    private String name;

    @Column("folder_path")
    private String folderPath;

    @Column("parent_folder_id")
    private Long parentFolderId;

    @Column("permission")
    private String permission;  // 권한 속성

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("modified_at")
    private LocalDateTime modifiedAt;

    @Column("has_files")
    private Boolean hasFiles;

}

