package com.tankmilu.webflux.record;

import java.time.LocalDateTime;
import java.util.List;

public record ContentsSearchResponse(
        Long contentsId,
        String title,
        String description,
        String type,
//        List<String> keywords,
        String thumbnailUrl,
        LocalDateTime modifiedAt
) {
}
