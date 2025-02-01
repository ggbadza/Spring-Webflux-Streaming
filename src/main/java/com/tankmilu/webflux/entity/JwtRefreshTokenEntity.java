package com.tankmilu.webflux.entity;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.Date;

@Builder
@Table()
public class JwtRefreshTokenEntity {

    @Id
    @Column("session_code")
    private String sessionCode;

    @Column("user_id")
    private String userId;

    @Column("issued_at")
    private Date issuedAt;

    @Column("expired_at")
    private Date expiredAt;
}
