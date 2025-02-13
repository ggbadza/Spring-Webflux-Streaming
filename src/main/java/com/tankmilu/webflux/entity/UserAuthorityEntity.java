package com.tankmilu.webflux.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table()
public class UserAuthorityEntity implements Persistable<String> {

    @Id
    @Getter
    @Column("user_id")
    private String userId;

    @Getter
    private String authority;

    // R2DBC에서 신규 엔티티는 Insert 수행하도록 하는 속성 값
    @Transient
    private boolean isNewRecord;

    // DB에서 엔티티 받아 올 경우
    public UserAuthorityEntity() {
        this.isNewRecord = false;
    }

    @Builder
    public UserAuthorityEntity(String userId, String authority) {
        this.userId = userId;
        this.authority = authority;
        this.isNewRecord = true; // 새로 생성하는 경우
    }

    @Override
    public String getId() {
        return this.userId;
    }

    @Override
    public boolean isNew() {
        return this.isNewRecord;
    }
}
