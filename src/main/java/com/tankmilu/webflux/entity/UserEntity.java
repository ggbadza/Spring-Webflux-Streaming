package com.tankmilu.webflux.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Table()
public class UserEntity {

    @Id
    @Getter
    private String userId;

    @Getter
    private String username; // 사용자 이름

    @Getter
    private String password; // 비밀번호

    @Getter
    private String subscriptionPlan; // 구독 플랜

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public List<String> getAuthorities() {
        // 권한 리스트 반환 (예: ROLE_USER, ROLE_ADMIN 등)
        return List.of("ROLE_USER");
    }


}
