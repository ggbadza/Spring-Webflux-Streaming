package com.tankmilu.webflux.record;

import java.util.List;

public record ContentsInfoWithFilesResponse(
        Long contentsId,
        String title,
        String description,
        String thumbnailUrl,
        String posterUrl,
        String type,
        List<FileInfoSummaryResponse> filesInfoList) {
}
