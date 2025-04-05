package com.tankmilu.webflux.record;

public record ContentsReponse(
        Long contentsId,
        String title,
        String description,
        String thumbnailUrl,
        String type,
        Long folderId) {
}
