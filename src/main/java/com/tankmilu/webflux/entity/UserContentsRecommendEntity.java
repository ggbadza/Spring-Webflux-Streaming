package com.tankmilu.webflux.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Table("user_contents_recommend")
@Builder
public class UserContentsRecommendEntity {

    @Id
    @Column("user_id")
    private String userId;

    @Column("recommend_seq")
    private Integer recommendSeq;

    @Column("description")
    private String description;

    @Column("contents_type")
    private String contentsType;

    @Column("folder_id")
    private Long folderId;

    @Column("created_at")
    @CreatedDate
    private LocalDateTime createdAt;

}
