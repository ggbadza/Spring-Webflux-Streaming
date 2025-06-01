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
        return folderPath + "/" + filePath;
    }

    public String getFullSubtitlePath() {
        return folderPath + "/" + subtitlePath;
    }

    public String getHeightPixel() {
        if (this.resolution == null || !this.resolution.contains("x")) {
            return null;
        }
        String[] parts = this.resolution.split("x");
        if (parts.length > 1) {
            return parts[1];
        }
        return null;
    }
}
