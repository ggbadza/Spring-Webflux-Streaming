package com.tankmilu.webflux.record;

public record ContentsResponse(
        Long contentsId,
        String title,
        String description,
        String thumbnailUrl,
        String posterUrl,
        String type,
        Long folderId) {
}
