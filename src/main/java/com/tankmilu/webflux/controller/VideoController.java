package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.VideoMonoRecord;
import com.tankmilu.webflux.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
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
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
@Slf4j
public class VideoController {

    private final VideoService videoService;

    @GetMapping("/test")
    public Mono<String> test(){
        return Mono.just("test");
    }

    @GetMapping("/testhls")
    public Mono<String> testhls() {
        return Mono.just("""
                <html>
                <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
                <body>
                  <video id="video" controls></video>
                  <script>
                    if (Hls.isSupported()) {
                      const video = document.getElementById('video');
                      const hls = new Hls({
                        debug: true, // 디버깅 활성화
                      });
                
                      hls.loadSource('http://127.0.0.1:8081/video/hlsm3u8?fn=video.mp4'); // M3U8 URL
                      hls.attachMedia(video);
                
                      hls.on(Hls.Events.MANIFEST_PARSED, function () {
                        console.log('Manifest loaded, starting playback');
                        video.play();
                      });
                
                      hls.on(Hls.Events.ERROR, function (event, data) {
                        console.error('HLS.js Error:', data);
                      });
                    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                      video.src = 'http://127.0.0.1:8081/video/hlsm3u8?fn=video.mp4';
                      video.addEventListener('loadedmetadata', function () {
                        video.play();
                      });
                    }
                  </script>
                </body>
                </html>
                """);
    }


    @GetMapping("/filerange")
    public Mono<ResponseEntity<Mono<DataBuffer>>> getVideoRange(
            @RequestParam String fn,
            @RequestParam(required = false) String bytes,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        String range;
        if (bytes != null) range="bytes="+bytes;
        else range = rangeHeader;

        VideoMonoRecord videoMonoRecord = videoService.getVideoChunk(fn, range);
        return Mono.just(
                ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.CONTENT_TYPE, videoMonoRecord.contentType())
                        .header(HttpHeaders.CONTENT_RANGE, videoMonoRecord.contentRange())
                        .body(videoMonoRecord.data())
        );
    }

    @GetMapping("/hlsm3u8")
    public Mono<ResponseEntity<String>> getHlsM3U8(
            @RequestParam String fn) throws IOException {
        return Mono.fromCallable(() -> videoService.getHlsM3u8(fn)) // 비동기 작업 래핑
                .subscribeOn(Schedulers.boundedElastic())           // 워커 쓰레드에 할당
                .map(data -> { // m3u8 텍스트를 ResponseEntity에 맵핑
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "application/x-mpegURL;");
                    return new ResponseEntity<>(data, headers, HttpStatus.OK);
                })
                .onErrorResume(e -> { // 에러 처리
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "text/plain;");
                    return Mono.just(new ResponseEntity<>("IO ERROR", headers, HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }

    @GetMapping("/hlsinit")
    public Mono<ResponseEntity<InputStreamResource>> getInitVideo(
            @RequestParam String fn) throws IOException {
        return Mono.fromCallable(() -> videoService.getHlsInitData(fn))
                .subscribeOn(Schedulers.boundedElastic())
                .map(data -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "video/mp4;");
                    return new ResponseEntity<>(data, headers, HttpStatus.OK);
                })
                .onErrorResume(e -> {
                    // 에러 메시지를 InputStreamResource로 반환
                    String errorMessage = "Error occurred: " + e.getMessage();
                    InputStream errorStream = new ByteArrayInputStream(errorMessage.getBytes(StandardCharsets.UTF_8));
                    InputStreamResource errorResource = new InputStreamResource(errorStream);
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "text/plain");
                    return Mono.just(new ResponseEntity<>(errorResource, headers, HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }

    @GetMapping("/hlsts")
    public Mono<ResponseEntity<InputStreamResource>> getTsVideo(
            @RequestParam String fn,
            @RequestParam String ss,
            @RequestParam String to) {
        return Mono.fromCallable(() -> videoService.getHlsTs(fn, ss, to))
                .subscribeOn(Schedulers.boundedElastic())
                .map(data -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "video/MP2T;");
                    return new ResponseEntity<>(data, headers, HttpStatus.OK);
                })
                .onErrorResume(e -> {
                    // 에러 메시지를 InputStreamResource로 반환
                    String errorMessage = "Error occurred: " + e.getMessage();
                    InputStream errorStream = new ByteArrayInputStream(errorMessage.getBytes(StandardCharsets.UTF_8));
                    InputStreamResource errorResource = new InputStreamResource(errorStream);
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "text/plain");
                    return Mono.just(new ResponseEntity<>(errorResource, headers, HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }
}
