package com.tankmilu.webflux.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Table("user_contents_following")
public class UserContentsFollowingEntity implements Persistable<String> {

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

    @Transient
    private boolean isNewRecord;

    @Override
    public String getId() {
        return this.userId;
    }

    @Override
    public boolean isNew() {
        return this.isNewRecord;
    }


    @Builder
    public UserContentsFollowingEntity(String userId, Integer followingSeq, Long contentsId, LocalDateTime createdAt) {
        this.userId = userId;
        this.followingSeq = followingSeq;
        this.contentsId = contentsId;
        this.createdAt = createdAt;
        this.isNewRecord = true; // 새로 생성하는 경우
    }

}
