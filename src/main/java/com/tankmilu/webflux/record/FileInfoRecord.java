package com.tankmilu.webflux.record;

import java.time.LocalDateTime;

public record FileInfoRecord(
        Long id,
        String fileName,
        String filePath,
        Long contentsId,
        String subtitlePath,
        String resolution,
        LocalDateTime createdAt,
        String folderPath,
        String subscriptionCode
) {
    public String getFullFilePath() {
        return folderPath + "\\" + filePath;
    }

    public String getFullSubtitlePath() {
        return folderPath + "\\" + subtitlePath;
    }
}
