package com.tankmilu.webflux.record;

public record FolderToContentsBatchRequest(
        String type  // 콘텐츠 유형 (anime, movie, drama)
) {
}
