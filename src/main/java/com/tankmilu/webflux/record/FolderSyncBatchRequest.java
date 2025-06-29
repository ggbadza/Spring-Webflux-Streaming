package com.tankmilu.webflux.record;

public record FolderSyncBatchRequest(
        String type,
        String directoryPath,
        String deleteYn
) {
}
