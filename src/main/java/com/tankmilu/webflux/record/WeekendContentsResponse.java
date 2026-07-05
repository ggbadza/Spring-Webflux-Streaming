package com.tankmilu.webflux.record;

public record WeekendContentsResponse(
        Long contentsId,
        String title,
        String description,
        String thumbnailUrl,
        String posterUrl,
        String backgroundUrl,
        String type,
        Long folderId,
        String folderName
) {
}
