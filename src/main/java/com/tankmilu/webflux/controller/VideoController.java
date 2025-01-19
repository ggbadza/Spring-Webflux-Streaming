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
@RequestMapping("${app.video.urls.base}")
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
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>HLS.js Resolution Selector</title>
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                            margin: 0;
                            padding: 0;
                            background-color: #f9f9f9;
                        }
                        video {
                            width: 80%;
                            max-width: 800px;
                            margin: 20px 0;
                        }
                        select {
                            font-size: 16px;
                            padding: 8px 12px;
                            margin-top: 10px;
                        }
                    </style>
                </head>
                <body>
                    <h1>HLS.js Resolution Selector</h1>
                    <video id="video" controls></video>
                    <select id="resolution-selector">
                        <option value="auto">Auto</option>
                    </select>
                
                    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
                    <script>
                        const video = document.getElementById('video');
                        const resolutionSelector = document.getElementById('resolution-selector');
                        const manifestUri = 'http://61.79.45.11:8081/video/hls_m3u8_master?fn=video.mkv'; // HLS URL
                
                        if (Hls.isSupported()) {
                            const hls = new Hls();
                
                            // Attach video element and load HLS source
                            hls.loadSource(manifestUri);
                            hls.attachMedia(video);
                
                            hls.on(Hls.Events.MANIFEST_PARSED, () => {
                                const levels = hls.levels;
                                resolutionSelector.innerHTML = '<option value="auto">Auto</option>';
                
                                // Populate resolution options
                                levels.forEach((level, index) => {
                                    const resolution = `${level.height}p`;
                                    const option = document.createElement('option');
                                    option.value = index;
                                    option.textContent = resolution;
                                    resolutionSelector.appendChild(option);
                                });
                
                                console.log('Available resolutions:', levels);
                            });
                
                            // Handle resolution change
                            resolutionSelector.addEventListener('change', (event) => {
                                const selectedIndex = event.target.value;
                
                                if (selectedIndex === 'auto') {
                                    hls.currentLevel = -1; // Auto
                                } else {
                                    hls.currentLevel = parseInt(selectedIndex, 10); // Set to specific level
                                }
                            });
                
                            hls.on(Hls.Events.LEVEL_SWITCHED, (_, data) => {
                                console.log(`Resolution switched to: ${hls.levels[data.level].height}p`);
                            });
                        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                            // Fallback for Safari (native HLS support)
                            video.src = manifestUri;
                        }
                    </script>
                </body>
                </html>
                """);
    }

    @GetMapping("/testhls2")
    public Mono<String> testhls2() {
        return Mono.just("""
                <!DOCTYPE html>
                       <html lang="en">
                       <head>
                           <meta charset="UTF-8">
                           <meta name="viewport" content="width=device-width, initial-scale=1.0">
                           <title>HLS.js Test Player</title>
                           <style>
                               body {
                                   display: flex;
                                   justify-content: center;
                                   align-items: center;
                                   height: 100vh;
                                   margin: 0;
                                   background-color: #f4f4f4;
                               }
                               #video-container {
                                   width: 80%;
                                   max-width: 800px;
                               }
                               video {
                                   width: 100%;
                                   height: auto;
                                   background-color: black;
                               }
                               .log-container {
                                   margin-top: 20px;
                                   max-width: 800px;
                                   font-family: Arial, sans-serif;
                                   font-size: 12px;
                                   line-height: 1.5;
                                   overflow: auto;
                                   max-height: 200px;
                                   background-color: #222;
                                   color: #eee;
                                   padding: 10px;
                                   border-radius: 5px;
                               }
                           </style>
                       </head>
                       <body>
                           <div id="video-container">
                               <video id="video" controls autoplay></video>
                           </div>
                           <div id="log-container" class="log-container">
                               <strong>Logs:</strong>
                               <pre id="log-output"></pre>
                           </div>
                
                           <!-- HLS.js -->
                           <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
                           <script>
                               document.addEventListener('DOMContentLoaded', () => {
                                   const video = document.getElementById('video');
                                   const logOutput = document.getElementById('log-output');
                                   const hls = new Hls({ debug: true }); // Debug 모드 활성화
                                   const manifestUrl = 'http://127.0.0.1:8081/video/hls_m3u8_fmp4?fn=video.mp4&type=3'; // 테스트 m3u8 URL
                
                                   // 로그 출력 함수
                                   function log(message) {
                                       const timestamp = new Date().toLocaleTimeString();
                                       logOutput.textContent += `[${timestamp}] ${message}\\n`;
                                       logOutput.scrollTop = logOutput.scrollHeight; // 자동 스크롤
                                   }
                
                                   // HLS.js 이벤트 핸들러
                                   hls.on(Hls.Events.MANIFEST_PARSED, (event, data) => {
                                       log(`Manifest loaded: ${data.levels.length} quality levels found.`);
                                       video.play();
                                   });
                
                                   hls.on(Hls.Events.ERROR, (event, data) => {
                                       log(`Error: ${data.type} - ${data.details}`);
                                       if (data.fatal) {
                                           log('A fatal error occurred, stopping playback.');
                                           hls.stopLoad();
                                       }
                                   });
                
                                   hls.on(Hls.Events.FRAG_LOADED, (event, data) => {
                                       log(`Fragment loaded: SN=${data.frag.sn}, Level=${data.frag.level}`);
                                   });
                
                                   // HLS.js 초기화
                                   if (Hls.isSupported()) {
                                       hls.loadSource(manifestUrl);
                                       hls.attachMedia(video);
                                       log('HLS.js is supported and initialized.');
                                   } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                                       video.src = manifestUrl; // 네이티브 HLS 지원 브라우저(Safari 등)
                                       log('Using native HLS playback.');
                                   } else {
                                       log('HLS is not supported on this browser.');
                                   }
                               });
                           </script>
                       </body>
                       </html>
                
                </html>
                """);
    }


    @GetMapping("${app.video.urls.filerange}")
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

    @GetMapping("${app.video.urls.hlsm3u8}")
    public Mono<ResponseEntity<String>> getHlsM3u8(
            @RequestParam String fn,
            @RequestParam(required = false, defaultValue = "0") String type) throws IOException {
        return Mono.fromCallable(() -> videoService.getHlsM3u8(fn,type)) // 비동기 작업 래핑
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

    @GetMapping("${app.video.urls.hlsm3u8fmp4}")
    public Mono<ResponseEntity<String>> getHlsM3u8Fmp4(
            @RequestParam String fn,
            @RequestParam(required = false, defaultValue = "0") String type) throws IOException {
        return Mono.fromCallable(() -> videoService.getHlsM3u8Fmp4(fn,type)) // 비동기 작업 래핑
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

    @GetMapping("${app.video.urls.hlsm3u8master}")
    public Mono<ResponseEntity<String>> getHlsM3u8Master(
            @RequestParam String fn) throws IOException {
        return Mono.fromCallable(() -> videoService.getHlsM3u8Master(fn)) // 비동기 작업 래핑
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

    @GetMapping("${app.video.urls.hlsinit}")
    public Mono<ResponseEntity<InputStreamResource>> getInitVideo(
            @RequestParam String fn) throws IOException {
        return Mono.fromCallable(() -> videoService.getHlsInitData(fn))
                .subscribeOn(Schedulers.boundedElastic())
                .map(data -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "application/zip;");
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

    @GetMapping("${app.video.urls.hlsts}")
    public Mono<ResponseEntity<InputStreamResource>> getTsVideo(
            @RequestParam String fn,
            @RequestParam String ss,
            @RequestParam String to,
            @RequestParam(required = false, defaultValue = "0") String type) {
        return Mono.fromCallable(() -> videoService.getHlsTs(fn, ss, to, type))
                .subscribeOn(Schedulers.boundedElastic())
                .map(data -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "video/mp2t;");
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

    @GetMapping("${app.video.urls.hlsfmp4}")
    public Mono<ResponseEntity<InputStreamResource>> getFmp4Video(
            @RequestParam String fn,
            @RequestParam String ss,
            @RequestParam String to,
            @RequestParam(required = false, defaultValue = "0") String type) {
        return Mono.fromCallable(() -> videoService.getHlsFmp4(fn, ss, to, type))
                .subscribeOn(Schedulers.boundedElastic())
                .map(data -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "application/zip;");
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
