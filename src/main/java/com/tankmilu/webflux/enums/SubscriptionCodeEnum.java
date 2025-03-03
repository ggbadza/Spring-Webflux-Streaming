package com.tankmilu.webflux.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public enum SubscriptionCodeEnum {
    PREMIUM("100"),
    STANDARD("101"),
    BASIC("102"),
    LITE("103");

    @Getter
    private final String permissionLevel;

    private static final Map<String, SubscriptionCodeEnum> BY_PERMISSION_LEVEL = new HashMap<String, SubscriptionCodeEnum>();

    static {
        for (SubscriptionCodeEnum code : values()) {
            BY_PERMISSION_LEVEL.put(code.getPermissionLevel(), code);
        }
    }

    SubscriptionCodeEnum(String permissionLevel) {
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
}
