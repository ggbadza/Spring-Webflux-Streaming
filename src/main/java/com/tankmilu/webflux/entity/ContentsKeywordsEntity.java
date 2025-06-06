package com.tankmilu.webflux.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("contents_keywords")
public class ContentsKeywordsEntity implements Persistable<Long> {

    @Id
    @Getter
    @Column("keyword_id")
    private Long keywordId;

    @Getter
    @Column("series_id")
    private String seriesId;        // 시리즈 ID

    @Getter
    @Setter
    @Column("keyword")
    private String keyword;        // 실제 파일 경로

    @Setter
    @Transient
    private boolean isNewRecord;

    @Builder
    public ContentsKeywordsEntity() {
    }

    @Override
    public Long getId() {
        return this.keywordId;
    }

    @Override
    public boolean isNew() {
        return this.isNewRecord;
    }
}
