package com.tankmilu.webflux.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("featured_banners")
@Getter
public class FeaturedBannersEntity implements Persistable<Long>, Serializable {

    @Id
    @Column("sequence_id")
    private Long sequenceId;

    @Column("contents_id")
    private Long contentsId;

    @Column("title")
    private String title;

    @Column("description")
    private String description;

    @Column("type")
    private String type;

    @Column("user_rating")
    private BigDecimal userRating;

    @Column("poster_url")
    private String posterUrl;

    @Column("thumbnail_url")
    private String thumbnailUrl;

    @Column("series_id")
    private String seriesId;

    @Column("season")
    private String season;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;


    @Setter
    @Transient
    private boolean isNewRecord;

    public FeaturedBannersEntity() {
        this.isNewRecord = false;
    }


    @Builder
    public FeaturedBannersEntity(
            Long sequenceId,
            Long contentsId,
            String title,
            String description,
            String type,
            BigDecimal userRating,
            String posterUrl,
            String thumbnailUrl,
            String seriesId,
            String season,
            LocalDateTime createdAt,
            LocalDateTime updatedAt)
    {
        this.sequenceId = sequenceId;
        this.contentsId = contentsId;
        this.title = title;
        this.description = description;
        this.type = type;
        this.userRating = userRating;
        this.posterUrl = posterUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.seriesId = seriesId;
        this.season = season;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isNewRecord = true;
    }

    @Override
    public Long getId() {
        return this.sequenceId;
    }

    @Override
    public boolean isNew() {
        return this.isNewRecord;
    }
}
