package com.tankmilu.webflux.entity;

import lombok.Builder;
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

    @Column("release_ym")
    @Getter
    private String releaseYM;

    @Column("type")
    @Getter
    private String type;

    @Column("folder_id")
    @Getter
    private Long folderId;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @CreatedDate
    @Column("modified_at")
    private LocalDateTime modifiedAt;

    @Transient
    private boolean isNewRecord;

    // DB에서 엔티티 받아 올 경우
    public ContentsObjectEntity() {
        this.isNewRecord = false;
    }

    @Builder
    public ContentsObjectEntity(
            Long contentsId,
            String title,
            String description,
            String thumbnailUrl,
            String posterUrl,
            String releaseYM,
            String type,
            Long folderId,
            LocalDateTime modifiedAt) {
        this.contentsId = contentsId;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.posterUrl = posterUrl;
        this.releaseYM = releaseYM;
        this.type = type;
        this.folderId = folderId;
        this.modifiedAt = modifiedAt;
        this.isNewRecord = true; // 새로 생성하는 경우
    }

    @Override
    public Long getId() {
        return this.contentsId;
    }

    @Override
    public boolean isNew() {
        return this.isNewRecord;
    }

}
