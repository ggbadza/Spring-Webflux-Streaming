package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.PlayListRecord;
import com.tankmilu.webflux.record.SubtitleMetadataResponse;
import com.tankmilu.webflux.record.VideoMonoRecord;
import com.tankmilu.webflux.security.CustomUserDetails;
import com.tankmilu.webflux.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
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

    /**
     * API 연결 테스트용 엔드포인트
     * 
     * @return "test" 문자열 반환
     */
    @GetMapping("/test")
    public Mono<String> test(){
        return Mono.just("test");
    }

    /**
     * 비디오 파일의 범위 요청을 처리함
     * 
     * @param fileId 비디오 파일 ID
     * @param rangeHeader HTTP Range 헤더 값
     * @return 요청된 범위의 비디오 데이터 반환
     */
    @GetMapping("${app.video.urls.filerange}")
    public Mono<Void> getVideoRange(
            @RequestParam Long fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            ServerHttpResponse response,
            @AuthenticationPrincipal CustomUserDetails userDetails) {


        return videoService.getVideoMeta(fileId, rangeHeader)
                .flatMap(videoMetaRecord -> {
                    response.setStatusCode(HttpStatus.PARTIAL_CONTENT);
                    response.getHeaders().add(HttpHeaders.ACCEPT_RANGES, "bytes");
                    response.getHeaders().add(HttpHeaders.CONTENT_TYPE, videoMetaRecord.contentType());
                    response.getHeaders().add(HttpHeaders.CONTENT_RANGE, videoMetaRecord.contentRange());
                    response.getHeaders().add(HttpHeaders.CONTENT_LENGTH, String.valueOf(videoMetaRecord.contentLength()));

                    Flux<DataBuffer> dataBufferFlux = videoService.getVideoDataBuffer(userDetails.getSubscriptionCode(), videoMetaRecord.videoPath(),videoMetaRecord.startByte(),videoMetaRecord.endByte());
                    return response.writeWith(dataBufferFlux);
                });
    }

    /**
     * HLS m3u8 플레이리스트 파일을 제공함
     * 
     * @param fileId 비디오 파일 ID
     * @param type 비디오 해상도 타입 (0: 원본 해상도, 1: 480p, 2: 720p, 3:1080p, 4:1440p)
     * @return HLS m3u8 플레이리스트 콘텐츠 반환
     */
    @GetMapping("${app.video.urls.hlsm3u8}")
    public Mono<ResponseEntity<String>> getHlsM3u8(
            @RequestParam Long fileId,
            @RequestParam(required = false, defaultValue = "0") String type) throws IOException {
        return videoService.getHlsM3u8(fileId, type)   // Mono<String> 반환
                .map(data -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "application/x-mpegURL");
                    return new ResponseEntity<>(data, headers, HttpStatus.OK);
                });
    }

    /**
     * HLS 마스터 플레이리스트를 제공함
     * 
     * @param fileId 비디오 파일 ID
     * @return 다양한 해상도 옵션이 포함된 HLS 마스터 플레이리스트 반환
     */
    @GetMapping("${app.video.urls.hlsm3u8master}")
    public Mono<ResponseEntity<String>> getHlsM3u8Master(@RequestParam Long fileId) {
        return videoService.getHlsM3u8Master(fileId)   // Mono<String> 반환
                .map(data -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "application/x-mpegURL");
                    return new ResponseEntity<>(data, headers, HttpStatus.OK);
                });
    }

    /**
     * 커스텀 비디오 플레이리스트를 제공함
     *
     * @param fileId 비디오 파일 ID
     * @return 다양한 해상도 옵션이 포함된 HLS 마스터 플레이리스트 반환
     */
    @GetMapping("${app.video.urls.playlist}")
    public Flux<PlayListRecord> getVideoPlayList(@RequestParam Long fileId) {
        return videoService.getVideoPlayList(fileId);
    }



    /**
     * HLS TS(Transport Stream) 세그먼트 파일을 제공함
     * 
     * @param fileId 비디오 파일 ID
     * @param ss 시작 시간(초)
     * @param to 종료 시간(초)
     * @param type 비디오 해상도 타입 (0: 원본 해상도, 1: 480p, 2: 720p, 3:1080p, 4:1440p)
     * @return 요청된 시간 범위의 TS 세그먼트 데이터 반환
     */
    @GetMapping(
            value    = "${app.video.urls.hlsts}",
            produces = "video/mp2t"
    )
    public Flux<DataBuffer> getTsVideo(
            @RequestParam Long fileId,
            @RequestParam String ss,
            @RequestParam String to,
            @RequestParam(required = false, defaultValue = "0") String type,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IOException {
        return videoService
                .getHlsTs(fileId, ss, to, type, userDetails.getSubscriptionCode())
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 비디오의 자막 파일을 제공함
     * 
     * @param fileId 비디오 파일 ID
     * @param type 자막 유형 ("f" : 파일 타입, "v{number}": 비디오 내장 {number}번째 자막)
     * @return 요청된 자막 파일 데이터 반환
     */
    @GetMapping(value = "${app.video.urls.subtitle}", produces = "text/plain; charset=UTF-8")
    public Flux<DataBuffer> getSubtitle(
            @RequestParam Long fileId,
            @RequestParam(required = false, defaultValue = "f") String type){
        return videoService.getSubtitle(fileId, type, "100");
    }

    /**
     * 자막 파일의 메타데이터 정보를 제공함
     * 
     * @param fileId 비디오 파일 ID
     * @return 자막 메타데이터 정보 반환
     */
    @GetMapping(value = "${app.video.urls.subtitlemetadata}")
    public Mono<SubtitleMetadataResponse> getSubtitleMetadata(
            @RequestParam Long fileId,
            @AuthenticationPrincipal CustomUserDetails userDetails){
        return videoService.getSubtitleMetadata(fileId, userDetails.getSubscriptionCode());
    }

}
