package com.tankmilu.webflux.record;

public record ContentsToFileBatchRequest(
        String type,      // 콘텐츠 유형 (anime, movie, drama)
        Long folderId     // 처리할 폴더 ID
) {
}
