package com.tankmilu.webflux.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("contents_quarter_entity")
public class ContentsQuarterEntity {

    @Id
    @Column("quarter_key")
    private String quarterKey;

    @Column("contents_id")
    private Long contentsId;

    @Column("poster_url")
    private String posterUrl;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("modified_at")
    private LocalDateTime modifiedAt;
}
