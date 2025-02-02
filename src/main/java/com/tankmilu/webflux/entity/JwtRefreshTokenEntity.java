package com.tankmilu.webflux.entity;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table()
public class JwtRefreshTokenEntity implements Persistable<String> {

    @Id
    @Column("session_code")
    private String sessionCode;

    @Column("user_id")
    private String userId;

    @Column("issued_at")
    private LocalDateTime issuedAt;

    @Column("expired_at")
    private LocalDateTime expiredAt;

    // R2DBC에서 신규 엔티티는 Insert 수행하도록 하는 속성 값
    @Transient
    private boolean isNewRecord;

    @Builder
    public JwtRefreshTokenEntity(String sessionCode, String userId, LocalDateTime issuedAt, LocalDateTime expiredAt) {
        this.sessionCode = sessionCode;
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.expiredAt = expiredAt;
        this.isNewRecord = true; // 새로 생성하는 경우
    }

    @Override
    public String getId() {
        return this.sessionCode;
    }

    @Override
    public boolean isNew() {
        return this.isNewRecord;
    }
}
