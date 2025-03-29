package com.tankmilu.webflux.record;

public record DirectoryRecord(
        Long folderId, // null 인 경우 File
        String name,
        Boolean hasFiles
) {
}
