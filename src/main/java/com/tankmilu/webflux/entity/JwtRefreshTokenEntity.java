package com.tankmilu.webflux.entity;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Date;

@Builder
@Table()
public class JwtRefreshTokenEntity {

    @Id
    private String sessionCode;

    private String userId;

    private Date issuedAt;

    private Date expiredAt;
}
