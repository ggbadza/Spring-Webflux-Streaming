package com.tankmilu.webflux.service;

import com.tankmilu.webflux.entity.ContentsFileEntity;
import com.tankmilu.webflux.enums.SubscriptionCodeEnum;
import com.tankmilu.webflux.enums.VideoResolutionEnum;
import com.tankmilu.webflux.record.SubtitleInfo;
import com.tankmilu.webflux.record.SubtitleMetadataResponse;
import com.tankmilu.webflux.record.VideoMonoRecord;
import com.tankmilu.webflux.repository.ContentsFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Value("${app.video.urls.hlsinit}")
    public String hlsinitUrl;

    @Value("${app.video.urls.hlsfmp4}")
    public String hlsfmp4Url;



    public VideoMonoRecord getVideoChunk(String name, String rangeHeader) {
        Path videoPath;

        String contentType = null;
        String rangeRes = null;
        long contentLength = 0;

        try {
            videoPath = new ClassPathResource("video/" + name).getFile().toPath(); // 비디오 파일 경로
        } catch (Exception e) {
            log.error(e.toString());
            return new VideoMonoRecord(contentType, rangeRes, contentLength, Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found")));
        }


        long fileLength = 0;
        try {
            fileLength = Files.size(videoPath);
            contentType = Optional.ofNullable(Files.probeContentType(videoPath)).orElse("video/mp4");
        } catch (IOException e) {
            log.error(e.toString());
            return new VideoMonoRecord(contentType, rangeRes, contentLength, Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "IO Error")));
        }
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

        // 람다함수용 final 변수
        long finalStart = start;
        long finalEnd = Math.min(start + CHUNK_SIZE - 1, end);
        int finalChunkSize = (int) Math.min(CHUNK_SIZE, end - start + 1);

        Mono<DataBuffer> videoMono = Mono.create(sink -> { // Consumer<FluxSink<T>> 객체 받음
            try {
                AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(videoPath, StandardOpenOption.READ);
                ByteBuffer buffer = ByteBuffer.allocate(finalChunkSize);
                // fileChannel에 콜백 함수 넘겨줌
                fileChannel.read(buffer, finalStart, null, new java.nio.channels.CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer result, Void attachment) {
                        if (result == -1) { // 파일의 끝에 도달 시 리턴
                            sink.success();
                            try {
                                fileChannel.close();
                            } catch (IOException ignored) {
                            }
                            return;
                        }

                        buffer.flip();
                        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(buffer);
                        sink.success(dataBuffer);
                        try {
                            fileChannel.close();
                        } catch (IOException ignored) {
                        }
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment) {
                        sink.error(exc);
                        try {
                            fileChannel.close();
                        } catch (IOException ignored) {
                        }
                    }
                });
            } catch (IOException e) {
                sink.error(e);
            }
        });
        rangeRes = "bytes " + finalStart + "-" + finalEnd + "/" + fileLength;
        return new VideoMonoRecord(contentType, rangeRes, contentLength, videoMono);
    }



    public String getHlsOriginal(String videoPath) throws IOException {
        List<List<String>> keyFrameStrings = ffmpegService.getVideoKeyFrame(videoPath);
        log.info(keyFrameStrings.toString());
        log.info(String.valueOf(keyFrameStrings.size()));
        StringBuilder m3u8Builder = new StringBuilder();

        m3u8Builder.append("#EXTM3U\n");
        m3u8Builder.append("#EXT-X-VERSION:7\n");
        m3u8Builder.append("#EXT-X-TARGETDURATION:20\n"); // 각 세그먼트 최대 길이 지정
//        m3u8Builder.append("#EXT-X-MEDIA-SEQUENCE:1\n\n");
        m3u8Builder.append("#EXT-X-PLAYLIST-TYPE:VOD\n");
        m3u8Builder.append("#EXT-X-MAP:URI=" + filerangeUrl + "?fn=" + videoPath + ".init.mp4\n\n");

        Double prevTime = 0.0;
        Double nowTime = 0.0;
        Integer prevBytes = Integer.valueOf(keyFrameStrings.get(0).get(2));
        Integer nowBytes = 0;
        String duration;
        for (List<String> keyFrame : keyFrameStrings) {
            if (prevTime + 10 < (nowTime = Double.parseDouble(keyFrame.get(1)))) {
                nowBytes = Integer.parseInt(keyFrame.get(2));
                duration = new BigDecimal(nowTime - prevTime).setScale(2, RoundingMode.CEILING).toPlainString();
                m3u8Builder.append("#EXTINF:" + duration + ",\n");
                m3u8Builder.append(filerangeUrl + "?fn=" + videoPath + "&bytes=" + String.valueOf(prevBytes) + "-" + String.valueOf(nowBytes - 1) + "\n");
                prevTime = nowTime;
                prevBytes = nowBytes;
            }
        }
        m3u8Builder.append("#EXTINF:10,\n");
        m3u8Builder.append(filerangeUrl + "?fn=" + videoPath + "&bytes=" + String.valueOf(nowBytes) + "-\n");
        m3u8Builder.append("\n");
        m3u8Builder.append("#EXT-X-ENDLIST");

        return m3u8Builder.toString();
    }

    public Mono<String> getHlsM3u8(Long fileId, String type) throws IOException {
        return contentsFileRepository.findById(fileId)
                .flatMap(entity -> // IO에러 처리를 위해 flatMap -> fromCallable 사용
                        Mono.fromCallable(() -> {
                            String videoPath = entity.getFilePath();
                            Double videoDuration = ffmpegService.getVideoDuration(videoPath);

                            StringBuilder m3u8Builder = new StringBuilder();

                            m3u8Builder
                                    .append("#EXTM3U\n")
                                    .append("#EXT-X-VERSION:7\n")
                                    .append("#EXT-X-TARGETDURATION:10\n")
                                    .append("#EXT-X-PLAYLIST-TYPE:VOD\n")
                                    .append("#EXT-X-MEDIA-SEQUENCE:0\n");
                            //        m3u8Builder.append("#EXT-X-MAP:URI="+"hlsinit?fn="+filename+"\n\n");
                            //        m3u8Builder.append("#EXT-X-MAP:URI="+"filerange?fn=init2.mp4\n\n");

                            // 세그먼트 최대 길이(초)
                            final int SEGMENT_LENGTH = 10;

                            for (double start = 0; start < videoDuration; start += SEGMENT_LENGTH) {
                                // 남은 길이와 최대 길이 중 작은 값을 세그먼트 길이로 선택
                                double duration = Math.min(SEGMENT_LENGTH, videoDuration - start);
                                double endTime = start + duration;

                                m3u8Builder
                                        .append("#EXTINF:").append(duration).append(",\n")
                                        .append(videoBaseUrl).append(hlstsUrl)
                                        .append("?fileId=").append(fileId)
                                        .append("&ss=").append(start)
                                        .append("&to=").append(endTime)
                                        .append("&type=").append(type)
                                        .append("\n");
                            }
                            m3u8Builder
                                    .append("\n")
                                    .append("#EXT-X-ENDLIST");
                            return m3u8Builder.toString();
                        })
                        // ffmpegService 같은 블로킹 IO는 boundedElastic 스케줄러로 실행
                        .subscribeOn(Schedulers.boundedElastic())
                );
    }


    public String getHlsM3u8Fmp4(String videoPath, String type) throws IOException {
        Double videoDuration = ffmpegService.getVideoDuration(videoPath);

        StringBuilder m3u8Builder = new StringBuilder();

        m3u8Builder.append("#EXTM3U\n");
        m3u8Builder.append("#EXT-X-VERSION:7\n");
        m3u8Builder.append("#EXT-X-TARGETDURATION:10\n");
        m3u8Builder.append("#EXT-X-PLAYLIST-TYPE:VOD\n");
        m3u8Builder.append("#EXT-X-MEDIA-SEQUENCE:0\n");
        m3u8Builder.append("#EXT-X-MAP:URI="+videoBaseUrl+hlsinitUrl+"?fn="+videoPath+"\n\n");
//        m3u8Builder.append("#EXT-X-MAP:URI="+"filerange?fn=init2.mp4\n\n");

        int nowTime = 0;
        while (videoDuration > 0) {
            if (videoDuration >= 10) {
                m3u8Builder.append("#EXTINF:10,\n");
                m3u8Builder.append(videoBaseUrl+hlsfmp4Url + "?fn=" + videoPath + "&ss=" + nowTime + "&to=" + (nowTime + 10) + "&type=" + type + "\n");
            } else {
                m3u8Builder.append("#EXTINF:" + videoDuration.toString() + "\n");
                m3u8Builder.append(videoBaseUrl+hlsfmp4Url + "?fn=" + videoPath + "&ss=" + nowTime + "&to=" + (nowTime + videoDuration) + "&type=" + type + "\n");
            }
            nowTime += 10;
            videoDuration -= 10;
        }

        m3u8Builder.append("\n");
        m3u8Builder.append("#EXT-X-ENDLIST");

        return m3u8Builder.toString();
    }

    public Mono<String> getHlsM3u8Master(Long fileId) {
        return contentsFileRepository.findById(fileId)
                .flatMap(entity -> // IO에러 처리를 위해 flatMap -> fromCallable 사용
                        Mono.fromCallable(() -> {
                            String videoPath = entity.getFilePath();
                            StringBuilder m3u8Builder = new StringBuilder();
                            m3u8Builder.append("#EXTM3U\n");
                            m3u8Builder.append("#EXT-X-VERSION:7\n");

                            Map<String, String> videoMetaData = ffmpegService.getVideoMetaData(videoPath);

                            // 지원 해상도 정보 추가
                            for (VideoResolutionEnum resolution : VideoResolutionEnum.values()) {
                                if (Integer.parseInt(videoMetaData.get("height")) >= resolution.getHeight()) {
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
                            if (!VideoResolutionEnum.isHeightSupported(height)) {
                                m3u8Builder.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                                        .append(videoMetaData.get("bandwidth"))
                                        .append(",RESOLUTION=")
                                        .append(videoMetaData.get("width"))
                                        .append("x").append(videoMetaData.get("height"))
                                        .append("\n")
                                        .append(videoBaseUrl).append(hlsm3u8Url)
                                        .append("?fileId=").append(fileId)
                                        .append("&type=0")
                                        .append("\n");
                            }

                            return m3u8Builder.toString();
                        })
                        // ffmpegService 같은 블로킹 IO는 boundedElastic 스케줄러로 실행
                        .subscribeOn(Schedulers.boundedElastic())
                );
    }


    public InputStreamResource getHlsInitData(String videoPath) throws IOException {
        return ffmpegService.getInitData(videoPath);
    }

//    public InputStreamResource getHlsTs(String videoPath, String start, String end, String type) throws IOException {
//        log.info("filename="+videoPath+",start="+start+",end="+end+",type="+type);
//        return ffmpegService.getTsData(videoPath, start, end, type);
//    }

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
                        return ffmpegService.getTsData(fileInfo.getFullPath(),start,end,type);
                    } catch (IOException e) {
                        return Flux.error(e);
                    }
                });
    }

    public Mono<SubtitleMetadataResponse> getSubtitleMetadata(Long fileId) {
        return contentsFileRepository.findById(fileId)
                .map(entity -> {
                    List<SubtitleInfo> subtitleInfoList = new ArrayList<>();
                    // 실제 자막 파일 존재시 첫 번째 항목에 추가
                    if (entity.getSubtitlePath() != null) {
                        subtitleInfoList.add(new SubtitleInfo("f", "kor"));
                    }
                    // 비디오 내부 자막 스트림 정보 추가
                    try {
                        subtitleInfoList.addAll(ffmpegService.getSubtitleMetaData(entity.getFilePath()));
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

    public Flux<DataBuffer> getSubtitle(Long fileId, String type) {
        log.info("fileId="+fileId+",type="+type);
        return switch (type.charAt(0)) {
            case 'f' ->
                // “f” 로 시작하면 파일에서 자막을 읽어온다
                    getSubtitleFromFile(fileId);
            case 'v' ->
                // “v” 로 시작하면 뒤에 오는 버전 문자열을 넘긴다
                getSubtitleFromVideo(fileId, type.substring(1));
            default -> Flux.error(new IllegalArgumentException("올바르지 않은 자막 타입입니다. type : " + type));
        };
    }

    public Flux<DataBuffer> getSubtitleFromFile(Long fileId) {
        log.info("### getSubtitleFromFile. fileId="+fileId);
        return contentsFileRepository.findById(fileId)
                // 파일이 없으면 404 에러 발생
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMapMany(entity -> {
                    String subPath = entity.getSubtitlePath();
                    // 경로가 Null 일시 404 에러 발생
                    if (subPath == null) {
                        return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
                    }
                    Path path = Paths.get(subPath);
                    // 파일 미존재 시 404 에러 발생
                    if (!Files.exists(path)) {
                        return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
                    }
                    return DataBufferUtils.read(path, dataBufferFactory, 4096);
                });
    }

    public Flux<DataBuffer> getSubtitleFromVideo(Long fileId, String subtitleId) {
        log.info("### getSubtitleFromVideo. fileId="+fileId+",subtitleId="+subtitleId);
        return contentsFileRepository.findById(fileId)
                // 파일이 없으면 404 에러 발생
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMapMany(entity -> {
                    String videoPath = entity.getFilePath();
                    try {
                        return ffmpegService.getSubtitleFromVideo(videoPath, subtitleId);
                    } catch (IOException e) {
                        return Flux.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR));
                    }
                });
    }

    public InputStreamResource getHlsFmp4(String videoPath, String start, String end, String type) throws IOException {
        log.info("filename="+videoPath+",start="+start+",end="+end+",type="+type);
        return ffmpegService.getFmp4Data(videoPath, start, end, type);
    }

}
