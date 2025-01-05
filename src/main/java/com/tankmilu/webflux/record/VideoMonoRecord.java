package com.tankmilu.webflux.record;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

public record VideoMonoRecord(
        String contentType,       // 비디오의 MIME 타입 (예: video/mp4)
        String contentRange,    // 비디오 데이터의 범위 (바이트 단위)
        long contentLength,       // 비디오 데이터의 길이 (바이트 단위)
        Mono<DataBuffer> data // 실제 비디오 데이터 값
) {
}
