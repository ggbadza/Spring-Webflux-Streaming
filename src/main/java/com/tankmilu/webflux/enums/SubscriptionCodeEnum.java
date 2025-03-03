package com.tankmilu.webflux.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public enum SubscriptionCodeEnum {
    PREMIUM("100", 100),
    STANDARD("101", 101),
    BASIC("102", 102),
    LITE("103", 103);

    @Getter
    private final String permissionCode;

    @Getter
    private final int permissionLevel;

    private static final Map<String, SubscriptionCodeEnum> BY_PERMISSION_LEVEL = new HashMap<>();

    static {
        for (SubscriptionCodeEnum code : values()) {
            BY_PERMISSION_LEVEL.put(code.getPermissionCode(), code);
        }
    }

    SubscriptionCodeEnum(String permissionCode, int permissionLevel) {
        this.permissionCode = permissionCode;
        this.permissionLevel = permissionLevel;
    }

    public static SubscriptionCodeEnum fromPermissionLevel(String permissionLevel) {
        SubscriptionCodeEnum code = BY_PERMISSION_LEVEL.get(permissionLevel);
        if (code == null) {
            throw new IllegalArgumentException("존재하지 않는 플랜 코드 입니다 : " + permissionLevel);
        }
        return code;
    }

    public static SubscriptionCodeEnum fromPermissionLevel(int permissionLevel) {
        String permissionLevelStr = String.valueOf(permissionLevel); // int를 문자열로 변환
        SubscriptionCodeEnum code = BY_PERMISSION_LEVEL.get(permissionLevelStr);
        if (code == null) {
            throw new IllegalArgumentException("존재하지 않는 플랜 코드 입니다 : " + permissionLevel);
        }
        return code;
    }

    public static Boolean comparePermissionLevel(String userPermissionCode, String contentsPermissionCode) {
        SubscriptionCodeEnum userSubscription = fromPermissionLevel(userPermissionCode);
        SubscriptionCodeEnum contentsSubscription = fromPermissionLevel(contentsPermissionCode);
        if (userSubscription.getPermissionLevel() <= contentsSubscription.getPermissionLevel() ) {
            return true; // 유저 권한이 콘텐츠 권한보다 클 경우
        }
        return false; // 유저 권한이 콘텐츠 권한보다 작을 경우
    }
}
