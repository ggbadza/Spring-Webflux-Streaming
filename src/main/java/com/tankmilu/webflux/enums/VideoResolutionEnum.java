package com.tankmilu.webflux.enums;

import lombok.Getter;

@Getter
public enum VideoResolutionEnum {
    RES_480P(854, 480, 800_000,"1"),
    RES_720P(1280, 720, 2_000_000,"2"),
    RES_1080P(1920, 1080, 4_000_000,"3"),
    RES_1440P(2560, 1440, 8_000_000,"4"),
//    RES_2160P(3840, 2160, 15_000_000,"5"),
    ;

    private final int width;
    private final int height;
    private final int bandwidth;
    private final String type;

    VideoResolutionEnum(int width, int height, int bandwidth, String type) {
        this.width = width;
        this.height = height;
        this.bandwidth = bandwidth;
        this.type = type;
    }

    public String getResolution() {
        return width + "x" + height;
    }

    public static boolean isHeightSupported(int height) {
        for (VideoResolutionEnum resolution : values()) {
            if (resolution.getHeight() == height) {
                return true;
            }
        }
        return false;
    }
}
