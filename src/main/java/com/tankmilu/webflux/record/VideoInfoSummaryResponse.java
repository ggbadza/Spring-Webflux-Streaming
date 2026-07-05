package com.tankmilu.webflux.record;

import java.time.LocalDateTime;

public record VideoInfoSummaryResponse(
        Long id,
        String fileName,
        Long contentsId,
        Boolean hasSubtitle,
        String resolution,
        LocalDateTime createdAt,
        String thumbnailUrl,
        String title,
        Integer progress,
        LocalDateTime watchedAt
) {
}
