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
import java.util.UUID;

@Table()
@Getter
public class ContentsObjectEntity implements Persistable<Long> {

    @Id
    @Column("contents_id")
    private Long contentsId;

    @Column("title")
    private String title;

    @Column("description")
    private String description;

    @Column("thumbnail_url")
    private String thumbnailUrl;

    @Column("poster_url")
    private String posterUrl;

    @Column("release_ym")
    private String releaseYM;

    @Column("type")
    private String type;

    @Column("folder_id")
    private Long folderId;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @CreatedDate
    @Column("modified_at")
    private LocalDateTime modifiedAt;

    @Column("subscription_code")
    private String subscriptionCode;

    @Column("series_id")
    private String seriesId;

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
            String subscriptionCode,
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
        this.subscriptionCode = subscriptionCode;
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
