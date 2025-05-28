package com.tankmilu.webflux.controller;

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
     * @param fn 비디오 파일명
     * @param bytes 요청 바이트 범위(선택적)
     * @param rangeHeader HTTP Range 헤더 값
     * @return 요청된 범위의 비디오 데이터 반환
     */
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
     * fMP4 형식의 HLS m3u8 플레이리스트를 제공함 (미사용)
     * 
     * @param fn 비디오 파일명
     * @param type 비디오 해상도 타입 (0: 원본 해상도, 1: 480p, 2: 720p, 3:1080p, 4:1440p)
     * @return fMP4 형식의 HLS m3u8 플레이리스트 콘텐츠 반환
     */
    @Deprecated
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


    /**
     * HLS 초기화 세그먼트를 제공함 (더 이상 사용되지 않음)
     * 
     * @param fn 비디오 파일명
     * @return HLS 초기화 세그먼트 데이터 반환
     */
    @Deprecated
    @GetMapping("${app.video.urls.hlsinit}")
    public Mono<ResponseEntity<InputStreamResource>> getInitVideo(
            @RequestParam String fn) {
        return Mono.fromCallable(() -> videoService.getHlsInitData(fn))
                .subscribeOn(Schedulers.boundedElastic())
                .map(data -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_TYPE, "video/mp4;");
                    return new ResponseEntity<>(data, headers, HttpStatus.OK);
                });
    }

    /**
     * HLS fMP4 세그먼트를 제공함 (더 이상 사용되지 않음)
     * 
     * @param fn 비디오 파일명
     * @param ss 시작 시간(초)
     * @param to 종료 시간(초)
     * @param type 비디오 해상도 타입 (0: 원본 해상도, 1: 480p, 2: 720p, 3:1080p, 4:1440p)
     * @return 요청된 시간 범위의 fMP4 세그먼트 데이터 반환
     */
    @Deprecated
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
                    headers.add(HttpHeaders.CONTENT_TYPE, "video/mp4;");
                    return new ResponseEntity<>(data, headers, HttpStatus.OK);
                });
    }
}
