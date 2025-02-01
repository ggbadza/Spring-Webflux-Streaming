package com.tankmilu.webflux.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Table()
public class UserEntity implements Persistable<String> {

    @Id
    @Getter
    @Column("user_id")
    private String userId;

    @Getter
    private String userName; // 사용자 이름

    @Getter
    private String password; // 비밀번호

    @Getter
    @Column("subscription_plan")
    private String subscriptionPlan; // 구독 플랜

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    // R2DBC에서 신규 엔티티는 Insert 수행하도록 하는 속성 값
    @Transient
    private boolean isNewRecord;

    public List<String> getAuthorities() {
        // 권한 리스트 반환 (예: ROLE_USER, ROLE_ADMIN 등)
        return List.of("ROLE_USER");
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
