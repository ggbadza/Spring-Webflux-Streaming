package com.tankmilu.webflux.record;

public record PlayListRecord(
        String videoType,
        String url,
        String pixel,
        Long fileId,
        String mimeType
) {
}
