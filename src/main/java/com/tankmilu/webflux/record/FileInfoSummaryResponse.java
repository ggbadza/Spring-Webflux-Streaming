package com.tankmilu.webflux.record;

import java.time.LocalDateTime;

public record FileInfoSummaryResponse(
        Long id,
        String fileName,
        Long contentsId,
        Boolean hasSubtitle,
        String resolution,
        LocalDateTime createdAt
) {
}
