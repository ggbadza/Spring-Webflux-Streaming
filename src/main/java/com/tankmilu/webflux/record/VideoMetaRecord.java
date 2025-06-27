package com.tankmilu.webflux.record;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

public record VideoMetaRecord(
        Path videoPath,
        String contentType,       // 비디오의 MIME 타입 (예: video/mp4)
        String contentRange,    // 비디오 데이터의 범위 (바이트 단위)
        long startByte,
        long endByte,
        long contentLength      // 비디오 데이터의 길이 (바이트 단위)
) {
}
