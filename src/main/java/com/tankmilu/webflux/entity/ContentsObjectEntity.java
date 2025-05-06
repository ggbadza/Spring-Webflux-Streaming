package com.tankmilu.webflux.entity;

import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table()
public class ContentsObjectEntity implements Persistable<Long> {

    @Id
    @Column("contents_id")
    @Getter
    private Long contentsId;

    @Column("title")
    @Getter
    private String title;

    @Column("description")
    @Getter
    private String description;

    @Column("thumbnail_url")
    @Getter
    private String thumbnailUrl;

    @Column("poster_url")
    @Getter
    private String posterUrl;

    @Column("release_year")
    @Getter
    private String releaseYear;

    @Column("type")
    @Getter
    private String type;

    @Column("folder_id")
    @Getter
    private Long folderId;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("modified_at")
    private LocalDateTime modifiedAt;

    @Transient
    private boolean isNewRecord;

    @Override
    public Long getId() {
        return this.contentsId;
    }

    @Override
    public boolean isNew() {
        return this.isNewRecord;
    }

}
