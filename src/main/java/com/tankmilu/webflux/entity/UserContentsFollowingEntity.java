package com.tankmilu.webflux.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Table("user_contents_following")
@Builder
public class UserContentsFollowingEntity {

    @Id
    @Column("user_id")
    private String userId;

    @Column("following_seq")
    private Integer followingSeq;

    @Column("contents_id")
    private Long contentsId;

    @Column("created_at")
    @CreatedDate
    private LocalDateTime createdAt;

}
