package com.tankmilu.webflux.service;

import com.tankmilu.webflux.enums.VideoResolutionEnum;
import com.tankmilu.webflux.record.VideoMonoRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private static final int CHUNK_SIZE = 1024 * 1024 * 10;

    private final FFmpegService ffmpegService;

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

    public String getHlsM3u8(String videoPath, String type) throws IOException {
        Double videoDuration = ffmpegService.getVideoDuration(videoPath);

        StringBuilder m3u8Builder = new StringBuilder();

        m3u8Builder.append("#EXTM3U\n");
        m3u8Builder.append("#EXT-X-VERSION:7\n");
        m3u8Builder.append("#EXT-X-TARGETDURATION:10\n");
        m3u8Builder.append("#EXT-X-PLAYLIST-TYPE:VOD\n");
        m3u8Builder.append("#EXT-X-MEDIA-SEQUENCE:0\n");
//        m3u8Builder.append("#EXT-X-MAP:URI="+"hlsinit?fn="+filename+"\n\n");
//        m3u8Builder.append("#EXT-X-MAP:URI="+"filerange?fn=init2.mp4\n\n");

        int nowTime = 0;
        while (videoDuration > 0) {
            if (videoDuration >= 10) {
                m3u8Builder.append("#EXTINF:10,\n");
                m3u8Builder.append(videoBaseUrl+hlstsUrl + "?fn=" + videoPath + "&ss=" + nowTime + "&to=" + (nowTime + 10) + "&type=" + type + "\n");
            } else {
                m3u8Builder.append("#EXTINF:" + videoDuration.toString() + "\n");
                m3u8Builder.append(videoBaseUrl+hlstsUrl + "?fn=" + videoPath + "&ss=" + nowTime + "&to=" + (nowTime + videoDuration) + "&type=" + type + "\n");
            }
            nowTime += 10;
            videoDuration -= 10;
        }

        m3u8Builder.append("\n");
        m3u8Builder.append("#EXT-X-ENDLIST");

        return m3u8Builder.toString();
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

    public String getHlsM3u8Master(String videoPath) throws IOException {
        StringBuilder m3u8Builder = new StringBuilder();

        m3u8Builder.append("#EXTM3U\n");
        m3u8Builder.append("#EXT-X-VERSION:7\n");

        Map<String, String> videoMetaData = ffmpegService.getVideoMetaData(videoPath);

        // Enum 타입에 정의 된 해상도 지원
        for (VideoResolutionEnum resolution : VideoResolutionEnum.values()) {
            if(Integer.parseInt(videoMetaData.get("height"))>=resolution.getHeight()){
                m3u8Builder.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                        .append(resolution.getBandwidth())
                        .append(",RESOLUTION=")
                        .append(resolution.getResolution())
                        .append("\n");
                m3u8Builder.append(videoBaseUrl+hlsm3u8Url + "?fn=" + videoPath + "&type=" + resolution.getType() + "\n");
            }
        }

        // 기본 해상도가 지원 해상도가 아닐 경우 원본 해상도 추가
        if (videoMetaData.get("height") != null && !VideoResolutionEnum.isHeightSupported(Integer.parseInt(videoMetaData.get("height")))) {
            m3u8Builder.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(videoMetaData.get("bandwidth"))
                    .append(",RESOLUTION=")
                    .append(videoMetaData.get("width") + "x" + videoMetaData.get("height"))
                    .append("\n");
            m3u8Builder.append(videoBaseUrl+hlsm3u8Url + "?fn=" + videoPath + "&type=0\n");
        }

        return m3u8Builder.toString();
    }

    public InputStreamResource getHlsInitData(String videoPath) throws IOException {
        return ffmpegService.getInitData(videoPath);
    }

    public InputStreamResource getHlsTs(String videoPath, String start, String end, String type) throws IOException {
        log.info("filename="+videoPath+",start="+start+",end="+end+",type="+type);
        return ffmpegService.getTsData(videoPath, start, end, type);
    }

    public InputStreamResource getHlsFmp4(String videoPath, String start, String end, String type) throws IOException {
        log.info("filename="+videoPath+",start="+start+",end="+end+",type="+type);
        return ffmpegService.getFmp4Data(videoPath, start, end, type);
    }

}
