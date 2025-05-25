package com.tankmilu.webflux.entity;


import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Table("registration_codes")
public class RegistrationCodeEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("reg_code")
    private String regCode;

    @Column("code_description")
    private String codeDescription;

    @Column("subscription_code")
    private String subscriptionCode;

    @Column("is_active")
    private Boolean isActive;

    @Column("max_usage_count")
    private Integer maxUsageCount;

    @Column("current_usage_count")
    private Integer currentUsageCount;

    @Column("valid_from")
    private LocalDateTime validFrom;

    @Column("valid_until")
    private LocalDateTime validUntil;

    @Column("created_by")
    private String createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 등록 코드가 현재 시점에서 유효한지 확인
     */
    public boolean isValidNow() {
        LocalDateTime now = LocalDateTime.now();
        return isActive != null && isActive
                && (validFrom == null || !now.isBefore(validFrom))
                && (validUntil == null || !now.isAfter(validUntil));
    }

    /**
     * 사용 가능한 횟수가 남아있는지 확인
     */
    public boolean hasUsageLeft() {
        return maxUsageCount == null || currentUsageCount < maxUsageCount;
    }

    /**
     * 등록 코드 사용 (사용 횟수 증가)
     */
    public void incrementUsage() {
        if (currentUsageCount == null) {
            currentUsageCount = 0;
        }
        currentUsageCount++;
    }

    /**
     * 등록 코드가 사용 가능한지 전체적으로 확인
     */
    public boolean isUsable() {
        return isValidNow() && hasUsageLeft();
    }
}
