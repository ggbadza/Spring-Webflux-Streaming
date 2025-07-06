package com.tankmilu.webflux.service;

import com.tankmilu.webflux.entity.ContentsFileEntity;
import com.tankmilu.webflux.enums.SubscriptionCodeEnum;
import com.tankmilu.webflux.enums.VideoResolutionEnum;
import com.tankmilu.webflux.exception.RangeNotSatisfiableException;
import com.tankmilu.webflux.record.*;
import com.tankmilu.webflux.repository.ContentsFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private static final int CHUNK_SIZE = 1024 * 1024 * 10;

    private final FFmpegService ffmpegService;

    private final ContentsFileRepository contentsFileRepository;

    private final DataBufferFactory dataBufferFactory;

    @Value("${app.video.urls.base}")
    public String videoBaseUrl;

    @Value("${app.video.urls.filerange}")
    public String filerangeUrl;

    @Value("${app.video.urls.hlsts}")
    public String hlstsUrl;

    @Value("${app.video.urls.hlsm3u8}")
    public String hlsm3u8Url;

    @Value("${custom.batch.subtitle_folder}")
    private String tempSubtitleFolder;

    @Value("${custom.batch.hls_folder}")
    private String tempHlsFolder;


    public Mono<VideoMetaRecord> getVideoMeta(Long fileId, String rangeHeader) {
        return contentsFileRepository.findFileWithContentInfo(fileId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "콘텐츠 파일 정보를 찾을 수 없습니다. ID: " + fileId)))
                .flatMap(fileInfo -> {
                    Path videoPath = Paths.get(fileInfo.getFullFilePath());

                    if (!Files.exists(videoPath) || !Files.isReadable(videoPath)) {
                        log.error("비디오 파일이 존재하지 않거나 읽을 수 없습니다: {}", videoPath);
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "비디오 파일을 찾을 수 없거나 접근할 수 없습니다."));
                    }

                    Mono<Long> fileLengthMono = Mono.fromCallable(() -> Files.size(videoPath))
                            .subscribeOn(Schedulers.boundedElastic());

                    Mono<String> contentTypeMono = Mono.fromCallable(() ->
                                    Optional.ofNullable(Files.probeContentType(videoPath)).orElse("video/mp4"))
                            .subscribeOn(Schedulers.boundedElastic());

                    return Mono.zip(fileLengthMono, contentTypeMono)
                            .flatMap(tuple -> {
                                long fileLength = tuple.getT1();
                                String contentType = tuple.getT2();

                                long start = 0;
                                long end = fileLength - 1; // 전체 파일의 마지막 바이트 인덱스

                                if (rangeHeader != null && !rangeHeader.isEmpty()) {
                                    List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
                                    if (!ranges.isEmpty()) {
                                        HttpRange range = ranges.get(0); // 첫 번째 범위만 사용
                                        start = range.getRangeStart(fileLength);
                                        end = range.getRangeEnd(fileLength); // 요청된 범위의 마지막 바이트 인덱스
                                    }
                                }

                                long currentChunkSize = end - start + 1;

                                if (currentChunkSize <= 0) {
                                    log.warn("계산된 청크 크기가 0 이하입니다. fileId: {}, range: {}, start: {}, end: {}, currentChunkSize: {}",
                                            fileId, rangeHeader, start, end, currentChunkSize);
                                    return Mono.error(new RangeNotSatisfiableException("계산된 청크 사이즈가 0 이하입니다."));
                                }

                                String rangeResponse = "bytes " + start + "-" + end + "/" + fileLength;

                                return Mono.just(new VideoMetaRecord(videoPath, contentType, rangeResponse, start, end, currentChunkSize));
                            })
                            .onErrorResume(IOException.class, e -> {
                                log.error("비디오 청크 처리 중 IOException 발생. fileId: {}, path: {}: {}", fileId, videoPath, e.getMessage());
                                return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "비디오 파일 읽기 중 오류 발생", e));
                            });
                })
                .onErrorResume(ResponseStatusException.class, Mono::error); // 이미 ResponseStatusException인 경우 그대로 전파
    }

    public Flux<DataBuffer> getVideoDataBuffer(String subscriptionCode, Path videoPath, long start, long end) {

        // 실제 전송할 청크 크기
        long currentChunkSize = end - start + 1;

        if (currentChunkSize <= 0) {
            log.warn("계산된 청크 크기가 0 이하입니다. videoPath: {}, start: {}, end: {}, currentChunkSize: {}",
                    videoPath, start, end, currentChunkSize);
            return Flux.error(new RangeNotSatisfiableException("계산된 청크 사이즈가 0 이하입니다."));
        }

        Resource videoResource = new FileSystemResource(videoPath);

        // 데이터버퍼 플럭스 생성
        Flux<DataBuffer> dataBufferFlux = DataBufferUtils.read(videoResource, start, dataBufferFactory, 4096);

//        // 시작 바이트 스킵
//        dataBufferFlux = DataBufferUtils.skipUntilByteCount(dataBufferFlux, start);
        // 종료 바이트 잘라냄
        dataBufferFlux = DataBufferUtils.takeUntilByteCount(dataBufferFlux, currentChunkSize);

        return dataBufferFlux
                .onErrorResume(ResponseStatusException.class, Flux::error) // 이미 ResponseStatusException인 경우 그대로 전파
                .onErrorResume(e -> { // 그 외 예상치 못한 에러 처리
                    log.error("getVideoChunk 처리 중 예상치 못한 오류 발생. videoPath: {}: {}", videoPath, e.getMessage(), e);
                    return Flux.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "비디오 청크를 가져오는 중 예상치 못한 오류 발생", e));
                });
    }



    public Mono<String> getHlsM3u8(Long fileId, String type) {
        Path tempFile = Paths.get(tempHlsFolder, fileId + "." + type + ".hls.m3u8");
        return contentsFileRepository.findFileWithContentInfo(fileId)
                .flatMap(entity ->
                        Mono.fromCallable(() -> Files.exists(tempFile))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(exists -> {
                                    if (exists) {
                                        // 캐싱 파일 존재 시 파일을 읽어서 그대로 반환
                                        log.info("M3U8 캐싱 파일 존재 : {}", tempFile);
                                        return Mono.fromCallable(() -> Files.readString(tempFile))
                                                .subscribeOn(Schedulers.boundedElastic());
                                    } else { // 트랜스코딩 형식
                                        // 캐싱 파일 미 존재 시 새로 생성 (트랜스코딩 형식)
                                        return Mono.fromCallable(() -> {
                                                    log.info("M3U8 캐싱 파일 미존재. 신규 생성 시도 : {}", tempFile);
                                                    String videoPath   = entity.getFullFilePath();

                                                    List<List<String>> keyFrameStrings = ffmpegService.getVideoKeyFrame(videoPath);
//                                                    double videoDuration = ffmpegService.getVideoDuration(videoPath);

                                                    StringBuilder m3u8Builder = new StringBuilder()
                                                            .append("#EXTM3U\n")
                                                            .append("#EXT-X-VERSION:7\n")
                                                            .append("#EXT-X-TARGETDURATION:10\n")
                                                            .append("#EXT-X-PLAYLIST-TYPE:VOD\n")
                                                            .append("#EXT-X-MEDIA-SEQUENCE:0\n");

                                                    final int SEGMENT_LENGTH = 10;

                                                    BigDecimal prevFrame = new BigDecimal("0.0");
                                                    BigDecimal nowFrame = new BigDecimal("0.0");;
                                                    BigDecimal segmentLengthBD = new BigDecimal(SEGMENT_LENGTH);
                                                    BigDecimal duration;

                                                    // 각 키 프레임을 순차적으로 비교
                                                    for (List<String> keyFrames : keyFrameStrings){
                                                        // 키 프레임이 이전 프레임+10을 넘어가면 입력.
                                                        nowFrame = new BigDecimal(keyFrames.get(1));
                                                        if (nowFrame.compareTo(prevFrame.add(segmentLengthBD)) >= 0){
                                                            duration = nowFrame.subtract(prevFrame);

                                                            m3u8Builder.append("#EXTINF:")
                                                                .append(duration.setScale(6, RoundingMode.HALF_UP).toPlainString()).append(",\n")
                                                                .append(videoBaseUrl).append(hlstsUrl)
                                                                .append("?fileId=").append(fileId)
                                                                .append("&ss=").append(prevFrame.toPlainString())
                                                                .append("&to=").append(nowFrame.toPlainString())
                                                                .append("&type=").append(type)
                                                                .append('\n');
                                                            prevFrame = nowFrame;
                                                        }
                                                    }
                                                    if (nowFrame.compareTo(prevFrame)>0){
                                                        duration = nowFrame.subtract(prevFrame);

                                                        m3u8Builder.append("#EXTINF:")
                                                                .append(duration.setScale(6, RoundingMode.HALF_UP).toPlainString()).append(",\n")
                                                                .append(videoBaseUrl).append(hlstsUrl)
                                                                .append("?fileId=").append(fileId)
                                                                .append("&ss=").append(prevFrame.toPlainString())
                                                                .append("&to=").append(nowFrame.toPlainString())
                                                                .append("&type=").append(type)
                                                                .append('\n');
                                                    }
                                                    m3u8Builder.append("\n").append("#EXT-X-ENDLIST");
                                                    return m3u8Builder.toString();
                                                })
                                                .subscribeOn(Schedulers.boundedElastic())
                                                .doOnNext(m3u8 -> {
                                                    try {
                                                        Files.createDirectories(tempFile.getParent());
                                                        Files.writeString(tempFile, m3u8, StandardOpenOption.CREATE,
                                                                StandardOpenOption.TRUNCATE_EXISTING);
                                                    } catch (IOException e) {
                                                        log.error("Error M3U8 파일 생성 실패 {}: {}", tempFile, e.getMessage());
                                                    }
                                                });
                                    }
                                })
                );
    }


    public Mono<String> getHlsM3u8Master(Long fileId) {
        log.info("getHlsM3u8Master, fileId=" + fileId);
        return contentsFileRepository.findFileWithContentInfo(fileId)
                .flatMap(entity -> // IO에러 처리를 위해 flatMap -> fromCallable 사용
                        Mono.fromCallable(() -> {
                                    log.info("@@@@@@@@@@@@@@@@@@@@entity="+entity);
                            String videoPath = entity.getFullFilePath();
                            StringBuilder m3u8Builder = new StringBuilder();
                            m3u8Builder.append("#EXTM3U\n");
                            m3u8Builder.append("#EXT-X-VERSION:7\n");

                            Map<String, String> videoMetaData = ffmpegService.getVideoMetaData(videoPath);

                            // 지원 해상도 정보 추가
                            for (VideoResolutionEnum resolution : VideoResolutionEnum.values()) {
                                if (Integer.parseInt(videoMetaData.get("height")) > resolution.getHeight()) {
                                    m3u8Builder.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                                            .append(resolution.getBandwidth())
                                            .append(",RESOLUTION=")
                                            .append(resolution.getResolution())
                                            .append("\n")
                                            .append(videoBaseUrl).append(hlsm3u8Url)
                                            .append("?fileId=").append(fileId)
                                            .append("&type=").append(resolution.getType())
                                            .append("\n");
                                }
                            }

                            // 원본 해상도(지원 리스트에 없을 때) 추가
                            int height = Integer.parseInt(videoMetaData.get("height"));
                            m3u8Builder.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                                    .append(videoMetaData.get("bandwidth"))
                                    .append(",RESOLUTION=")
                                    .append(videoMetaData.get("width")).append("x").append(videoMetaData.get("height"))
                                    .append("\n")
                                    .append(videoBaseUrl).append(hlsm3u8Url)
                                    .append("?fileId=").append(fileId)
                                    .append("&type=").append(VideoResolutionEnum.RES_ORIGINAL.getType())
                                    .append("\n");

                            return m3u8Builder.toString();
                        })
                        // ffmpegService 같은 블로킹 IO는 boundedElastic 스케줄러로 실행
                        .subscribeOn(Schedulers.boundedElastic())
                );
    }


    public Flux<DataBuffer> getHlsTs(Long fileId, String start, String end, String type, String userPlan) {
        log.info("fileId={},start={},end={},type={}, userPlan={}", fileId, start, end, type, userPlan);
        return contentsFileRepository.findFileWithContentInfo(fileId)
                // Mono -> Flux 변환
                .flatMapMany(fileInfo  ->
                {
                    if (!SubscriptionCodeEnum.comparePermissionLevel(userPlan, fileInfo.subscriptionCode())) {
                        throw new AccessDeniedException("폴더에 대한 권한이 없습니다.");
                    }
                    try {
                        return ffmpegService.getTsData(fileInfo.getFullFilePath(),start,end,type);
                    } catch (IOException e) {
                        return Flux.error(e);
                    }
                });
    }

    public Mono<SubtitleMetadataResponse> getSubtitleMetadata(Long fileId, String userPlan) {
        return contentsFileRepository.findFileWithContentInfo(fileId)
                .map(fileInfo -> {
                    List<SubtitleInfo> subtitleInfoList = new ArrayList<>();
                    if (!SubscriptionCodeEnum.comparePermissionLevel(userPlan, fileInfo.subscriptionCode())) {
                        throw new AccessDeniedException("폴더에 대한 권한이 없습니다.");
                    }
                    // 실제 자막 파일 존재시 첫 번째 항목에 추가
                    if (fileInfo.subtitlePath() != null && !fileInfo.subtitlePath().isEmpty()) {
                        subtitleInfoList.add(new SubtitleInfo("f", "kor"));
                    }
                    // 비디오 내부 자막 스트림 정보 추가
                    try {
                        subtitleInfoList.addAll(ffmpegService.getSubtitleMetaData(fileInfo.getFullFilePath()));
                    } catch (IOException e) {
                        log.error("getSubtitleMetadata", e);
                    }

                    String hasSubtitle = subtitleInfoList.isEmpty() ? "N" : "Y";
                    int count = subtitleInfoList.size();

                    return new SubtitleMetadataResponse(
                            hasSubtitle,
                            count,
                            subtitleInfoList
                    );
                });
    }

    public Flux<DataBuffer> getSubtitle(Long fileId, String type, String userPlan) {
        log.info("fileId="+fileId+",type="+type);
        return switch (type.charAt(0)) {
            case 'f' ->
                // “f” 로 시작하면 파일에서 자막을 읽어온다
                    getSubtitleFromFile(fileId, userPlan);
            case 'v' ->
                // “v” 로 시작하면 뒤에 오는 버전 문자열을 넘긴다
                getSubtitleFromVideo(fileId, type.substring(1), userPlan);
            default -> Flux.error(new IllegalArgumentException("올바르지 않은 자막 타입입니다. type : " + type));
        };
    }

    public Flux<DataBuffer> getSubtitleFromFile(Long fileId, String userPlan) {
        log.info("### getSubtitleFromFile. fileId="+fileId);
        return contentsFileRepository.findFileWithContentInfo(fileId)
                // 파일이 없으면 404 에러 발생
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMapMany(fileInfo -> {
                    // 권한 미 존재시 에러 발생
                    if (!SubscriptionCodeEnum.comparePermissionLevel(userPlan, fileInfo.subscriptionCode())) {
                        throw new AccessDeniedException("컨텐츠에 대한 권한이 없습니다.");
                    }
                    // 경로가 Null 일시 404 에러 발생
                    if (fileInfo.subtitlePath() == null) {
                        return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
                    }

                    String tempSubtitlePath = tempSubtitleFolder+"/"+fileId+".f.ass";
                    Path pathTemp = Paths.get(tempSubtitlePath);
                    if (Files.exists(pathTemp)) {
                        log.info("캐싱 자막 파일 존재 확인 : {}",tempSubtitlePath);
                        return DataBufferUtils.read(pathTemp, dataBufferFactory, 4096);
                    }
                    Path path = Paths.get(fileInfo.getFullSubtitlePath());
                    // 파일 미존재 시 404 에러 발생
                    if (!Files.exists(path)) {
                        return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
                    }
                    log.info("캐싱 파일 미 존재. 기존 파일을 가져옵니다 : {}",path);
                    return DataBufferUtils.read(path, dataBufferFactory, 4096);
                });
    }

    public Flux<DataBuffer> getSubtitleFromVideo(Long fileId, String subtitleId, String userPlan) {
        log.info("### getSubtitleFromVideo: fileId={}, subtitleId={}, userPlan={}", fileId, subtitleId, userPlan);
        // 캐시 파일 경로
        Path tempCachePath = Paths.get(tempSubtitleFolder, fileId + ".v" + subtitleId + ".ass");

        return contentsFileRepository.findFileWithContentInfo(fileId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "콘텐츠 정보를 찾을 수 없습니다. fileId: " + fileId)))
                .flatMapMany(fileInfo -> {
                    if (!SubscriptionCodeEnum.comparePermissionLevel(userPlan, fileInfo.subscriptionCode())) {
                        return Flux.error(new AccessDeniedException("요청된 콘텐츠에 대한 접근 권한이 없습니다."));
                    }

                    // 1. 캐시 존재 여부 비동기 확인
                    return Mono.fromCallable(() -> Files.exists(tempCachePath))
                            .subscribeOn(Schedulers.boundedElastic()) // Files.exists는 블로킹 I/O
                            .flatMapMany(isCached -> {
                                if (isCached) {
                                    // 2-1. 캐시된 파일이 있으면 해당 파일 스트림 반환
                                    log.info("캐시된 자막 파일 사용: {}", tempCachePath);
                                    return DataBufferUtils.read(tempCachePath, dataBufferFactory, 4096);
                                } else {
                                    // 2-2. 캐시된 파일이 없으면 FFmpeg 통해 생성
                                    log.info("캐시된 자막 파일 없음. FFmpeg 통해 생성 : {}", tempCachePath);

                                    // FFmpeg 서비스로부터 자막 스트림 가져오기
                                    Flux<DataBuffer> liveSubtitles;
                                    try {
                                        liveSubtitles = ffmpegService.getSubtitleFromVideo(fileInfo.getFullFilePath(), subtitleId)
                                                .onErrorMap(originalError -> {
                                                    // FFmpeg 서비스에서 발생한 원본 에러 로깅
                                                    log.error("FFmpeg 자막 추출 중 오류 발생 (fileId: {}, subtitleId: {}): {}",
                                                            fileId, subtitleId, originalError.getMessage(), originalError);
                                                    // 클라이언트에게 전달될 에러로 변환
                                                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "자막 스트림 생성 중 내부 오류가 발생했습니다.", originalError);
                                                });
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }

                                    Flux<DataBuffer> streamToClientAndCache = liveSubtitles.cache();

                                    // 캐싱 작업
                                    DataBufferUtils.write(streamToClientAndCache, tempCachePath)
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .doOnSuccess(v -> log.info("자막 캐싱 성공: {}", tempCachePath))
                                            .doOnError(cacheErr -> log.error("자막 캐싱 실패 {}: {}", tempCachePath, cacheErr.getMessage(), cacheErr))
                                            .onErrorResume(cacheErr -> Mono.empty()) //
                                            .subscribe();

                                    return streamToClientAndCache;
                                }
                            });
                });
    }

    public Flux<PlayListRecord> getVideoPlayList(Long fileId){
        return contentsFileRepository.findFileWithContentInfo(fileId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "콘텐츠 정보를 찾을 수 없습니다. fileId: " + fileId)))
                .flatMapMany(entity -> {
                    String heightPixel;
                    // DB에 픽셀 값이 저장되어있지 않을 경우
                    if(entity.getHeightPixel()==null){
                        log.info("fileId: {}의 해상도 값 미존재. FFMPEG를 통해서 받아옵니다.", fileId);
                        try {
                            Map<String, String> videoMetaData = ffmpegService.getVideoMetaData(entity.getFullFilePath());
                            heightPixel =  videoMetaData.get("height");
                            if (heightPixel == null) {
                                log.error("fileId: {}에 대해 FFMPEG가 비디오 높이 정보를 반환하지 않았습니다.", fileId);
                                return Flux.error(new RuntimeException("FFMPEG에서 비디오 높이 정보를 가져오지 못했습니다. fileId: " + fileId));
                            }
                            // db에 값 저장
                            final String resolution = videoMetaData.get("width") + "x" + videoMetaData.get("height");
                            log.debug("R2DBC - fileId: {}의 해상도 정보 '{}' 캐싱을 시도합니다.", fileId, resolution);

                            return Flux.fromIterable(getPlayListRecords(fileId, heightPixel))
                                    .publishOn(Schedulers.boundedElastic())
                                    .doOnComplete(() -> { // 플레이리스트 생성이 성공적으로 완료된 후 DB 저장 시도
                                        log.info("fileId : {}의 플레이리스트 생성 완료. 해상도 정보 ('{}') 캐싱을 시도합니다.", fileId, resolution);

                                        contentsFileRepository.findById(fileId) // 엔티티를 다시 로드하여 업데이트
                                                .flatMap(entityToUpdate -> {
                                                    entityToUpdate.setResolution(resolution);
                                                    entityToUpdate.setNewRecord(false);
                                                    return contentsFileRepository.save(entityToUpdate);
                                                })
                                                .doOnSuccess(savedEntity -> log.info("R2DBC - fileId: {}의 해상도 정보 캐싱 성공.", fileId))
                                                .doOnError(e -> log.error("R2DBC - fileId: {}의 해상도 정보 캐싱 실패. Error: {}", fileId, e.getMessage()))
                                                .onErrorResume(e -> Mono.empty())
                                                .subscribe();
                                    });

                        } catch (IOException e) {
                            log.error("fileId: {}의 메타데이터 조회 중 FFMPEG IOException 발생: {}", fileId, e.getMessage());
                            return Flux.error(new RuntimeException("FFMPEG 처리 중 오류 발생 fileId: " + fileId, e));
                        }
                    } else {
                        heightPixel = entity.getHeightPixel();
                        log.info("fileId: {}의 해상도 값 존재. : {}", fileId,heightPixel);
                        return Flux.fromIterable(getPlayListRecords(fileId, heightPixel));
                    }
                });
    }

    // playList 추출 메소드 분리
    private List<PlayListRecord> getPlayListRecords(Long fileId, String heightPixel) {
        List<PlayListRecord> playListRecords = new ArrayList<>();

        // 지원하는 비디오 Enum을 이용해 리스트 생성
        for (VideoResolutionEnum resolution : VideoResolutionEnum.values()) {
            if (Integer.parseInt(heightPixel) >= resolution.getHeight()) {
                PlayListRecord record = new PlayListRecord(
                        resolution.getType(),
                        videoBaseUrl+hlsm3u8Url+"?fileId="+ fileId +"&type="+resolution.getType(),
                        String.valueOf(resolution.getHeight())+"p",
                        fileId,
                        "application/x-mpegURL"
                );
                playListRecords.add(record);
            }
        }
        // 정규 해상도가 아니면 최종적으로 한번 더 넣음
        if (!VideoResolutionEnum.isHeightSupported(Integer.parseInt(heightPixel))){
            PlayListRecord record = new PlayListRecord(
                    "0",
                    videoBaseUrl+hlsm3u8Url+"?fileId="+ fileId +"&type=0",
                    heightPixel+"p",
                    fileId,
                    "application/x-mpegURL"
            );
            playListRecords.add(record);
        }

        // 원본 비디오 리스트 최종 추가
        PlayListRecord record = new PlayListRecord(
                "99",
                videoBaseUrl+filerangeUrl+"?fileId="+ fileId,
                "원본",
                fileId,
                "video/mp4"
        );
        playListRecords.add(record);
        return playListRecords;
    }

    public Mono<Boolean> buildM3u8Content(Long fileId, Path videoPath) {
        return Mono.fromCallable(() -> {
            List<List<String>> keyFrameStrings = ffmpegService.getVideoKeyFrame(videoPath.toString());
            double videoDuration = ffmpegService.getVideoDuration(videoPath.toString());

            // 정의된 모든 해상도(enum)에 대해 반복
            for (VideoResolutionEnum resolution : VideoResolutionEnum.values()) {
                String type = resolution.getType();

                StringBuilder m3u8Builder;
                final int SEGMENT_LENGTH = 10;
                BigDecimal prevFrame = new BigDecimal("0.0");
                BigDecimal nowFrame = new BigDecimal("0.0");
                BigDecimal segmentLengthBD = new BigDecimal(SEGMENT_LENGTH);
                BigDecimal duration;

                try {
                    // 각 해상도 타입에 맞는 M3U8 파일 내용을 생성.
                    m3u8Builder = new StringBuilder()
                            .append("#EXTM3U\n")
                            .append("#EXT-X-VERSION:7\n")
                            .append("#EXT-X-TARGETDURATION:10\n")
                            .append("#EXT-X-PLAYLIST-TYPE:VOD\n")
                            .append("#EXT-X-MEDIA-SEQUENCE:0\n");

                    // 각 키프레임을 순회하며 세그먼트를 나눕니다.
                    for (List<String> keyFrames : keyFrameStrings) {
                        nowFrame = new BigDecimal(keyFrames.get(1));
                        if (nowFrame.compareTo(prevFrame.add(segmentLengthBD)) >= 0) {
                            duration = nowFrame.subtract(prevFrame);

                            m3u8Builder.append("#EXTINF:")
                                    .append(duration.setScale(6, RoundingMode.HALF_UP).toPlainString()).append(",\n")
                                    .append(videoBaseUrl).append(hlstsUrl)
                                    .append("?fileId=").append(fileId)
                                    .append("&ss=").append(prevFrame.toPlainString())
                                    .append("&to=").append(nowFrame.toPlainString())
                                    .append("&type=").append(type) // 현재 해상도 타입 적용
                                    .append('\n');
                            prevFrame = nowFrame;
                        }
                    }
                    // 마지막 남은 세그먼트를 처리
                    if (nowFrame.compareTo(prevFrame) > 0) {
                        duration = nowFrame.subtract(prevFrame);

                        m3u8Builder.append("#EXTINF:")
                                .append(duration.setScale(6, RoundingMode.HALF_UP).toPlainString()).append(",\n")
                                .append(videoBaseUrl).append(hlstsUrl)
                                .append("?fileId=").append(fileId)
                                .append("&ss=").append(prevFrame.toPlainString())
                                .append("&to=").append(nowFrame.toPlainString())
                                .append("&type=").append(type) // 현재 해상도 타입 적용
                                .append('\n');
                    }
                    m3u8Builder.append("#EXT-X-ENDLIST");

                } catch (Exception e){ // 키프레임 에러 시 10초 단위로 나누도록 설정
                    // 각 해상도 타입에 맞는 M3U8 파일 내용을 생성.
                    m3u8Builder = new StringBuilder()
                            .append("#EXTM3U\n")
                            .append("#EXT-X-VERSION:7\n")
                            .append("#EXT-X-TARGETDURATION:10\n")
                            .append("#EXT-X-PLAYLIST-TYPE:VOD\n")
                            .append("#EXT-X-MEDIA-SEQUENCE:0\n");

                    duration = new BigDecimal(videoDuration);
                    nowFrame = nowFrame.add(segmentLengthBD);

                    while(nowFrame.compareTo(duration)<0){
                        m3u8Builder.append("#EXTINF:")
                                .append(duration.setScale(6, RoundingMode.HALF_UP).toPlainString()).append(",\n")
                                .append(videoBaseUrl).append(hlstsUrl)
                                .append("?fileId=").append(fileId)
                                .append("&ss=").append(prevFrame.toPlainString())
                                .append("&to=").append(nowFrame.toPlainString())
                                .append("&type=").append(type) // 현재 해상도 타입 적용
                                .append('\n');
                        prevFrame=nowFrame;
                        nowFrame=nowFrame.add(segmentLengthBD);
                    }
                    m3u8Builder.append("#EXTINF:")
                            .append(duration.setScale(6, RoundingMode.HALF_UP).toPlainString()).append(",\n")
                            .append(videoBaseUrl).append(hlstsUrl)
                            .append("?fileId=").append(fileId)
                            .append("&ss=").append(prevFrame.toPlainString())
                            .append("&to=").append(duration.toPlainString())
                            .append("&type=").append(type) // 현재 해상도 타입 적용
                            .append('\n');
                    m3u8Builder.append("#EXT-X-ENDLIST");

                }



                // 해상도별 M3U8 파일을 저장.
                Path tempFile = Paths.get(tempHlsFolder, fileId + "." + type + ".hls.m3u8");

                // 상위 디렉토리가 없으면 생성합니다.
                try {
                    Files.createDirectories(tempFile.getParent());
                    Files.writeString(tempFile, m3u8Builder.toString(), StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    log.error("Error M3U8 파일 생성 실패 {}: {}", tempFile, e.getMessage());
                    return false;
                }
            }

            // 5. 모든 작업이 예외 없이 완료되면 true를 반환합니다.
            return true;
        }).doOnError(error -> {
            // 에러 발생 시 로그를 남길 수 있습니다.
            System.err.println("M3U8 파일 생성 중 오류 발생: " + error.getMessage());
        });
    }


}
