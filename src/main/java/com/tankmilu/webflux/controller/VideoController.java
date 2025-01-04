package com.tankmilu.webflux.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
@Slf4j
public class VideoController {

    private static final int CHUNK_SIZE = 1024 * 1024;


    @GetMapping("/test")
    public Mono<String> getVideo() {
        return Mono.just("test");
    }


    @GetMapping("/{name}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> getVideo(
            @PathVariable String name,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        File videoFile;
        try {
            videoFile = ResourceUtils.getFile(STR."classpath:video/\{name}"); // 비디오 파일 경로
        } catch (IOException e) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
        }

        // 비디오 파일 속성
        long fileLength = videoFile.length();
        long start = 0;
        long end = fileLength - 1;

        // Range 헤더 파싱
        if (rangeHeader != null) {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            if (!ranges.isEmpty()) {
                HttpRange range = ranges.get(0); // 범위가 여러 개 일시 첫번째꺼만 가져옴
                start = range.getRangeStart(fileLength);
                end = range.getRangeEnd(fileLength);
            }
        }

        // 비디오 파일의 해당 범위를 읽어 스트리밍
        Resource resource = new FileSystemResource(videoFile);
        Flux<DataBuffer> videoFlux = DataBufferUtils.read(resource, new DefaultDataBufferFactory(), CHUNK_SIZE)
                .skip(start / CHUNK_SIZE)  // 청크 단위로 건너뛰기
                .take((end - start + 1) / CHUNK_SIZE); // 끝 범위까지 가져오기

        return Mono.just(
                ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                        .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength)
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(end - start + 1))
                        .body(videoFlux)
        );
    }
}
