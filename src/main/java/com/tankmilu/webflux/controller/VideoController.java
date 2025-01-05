package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.VideoMonoRecord;
import com.tankmilu.webflux.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
@Slf4j
public class VideoController {

    @Autowired
    private final VideoService videoService;

    @GetMapping("/test")
    public Mono<String> test() {
        return Mono.just("test");
    }

    @GetMapping("/{name}")
    public Mono<ResponseEntity<Mono<DataBuffer>>> getVideo(
            @PathVariable String name,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        VideoMonoRecord videoMonoRecord = videoService.getVideoChunk(name, rangeHeader);
        return Mono.just(
                ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.CONTENT_TYPE, videoMonoRecord.contentType())
                        .header(HttpHeaders.CONTENT_RANGE, videoMonoRecord.contentRange())
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(videoMonoRecord.contentLength()))
                        .body(videoMonoRecord.data())
        );
    }
}
