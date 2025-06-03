package com.tankmilu.webflux.service;

import com.tankmilu.webflux.enums.VideoResolutionEnum;
import com.tankmilu.webflux.record.SubtitleInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import net.bramp.ffmpeg.shared.CodecType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FFmpegServiceProcessImpl implements FFmpegService {

    @Value("${custom.ffmpeg.ffmpeg}")
    public String ffmpegDir;

    @Value("${custom.ffmpeg.ffprobe}")
    public String ffprobeDir;

    private final DataBufferFactory dataBufferFactory;

    @Override
    public HashMap<String, String> getVideoMetaData(String videoPath) throws IOException {
        FFprobe ffprobe = new FFprobe(ffprobeDir);
        log.info("ffprobe start. videoPath: {}", videoPath);
//        String videoPath = new ClassPathResource("video/" + filename).getFile().getPath();
        FFmpegProbeResult probeResult = ffprobe.probe(videoPath);

        HashMap<String, String> metaData = new HashMap<>();

        // 해시맵에 입력
        metaData.put("format", probeResult.getFormat().format_name);
        metaData.put("duration", String.valueOf(probeResult.getFormat().duration));
        metaData.put("bitrate", String.valueOf(probeResult.getFormat().bit_rate));
        metaData.put("size", String.valueOf(probeResult.getFormat().size));
        // 비디오 해상도 추출
        FFmpegStream stream = probeResult.getStreams().get(0);

        if (stream != null) {
            metaData.put("width", String.valueOf(stream.width));
            metaData.put("height", String.valueOf(stream.height));
            metaData.put("video_codec", stream.codec_name);
        }

        log.info("ffmpeg probe result: {}", metaData);
        return metaData;
    }

    public List<SubtitleInfo> getSubtitleMetaData(String videoPath) throws IOException {
        FFprobe ffprobe = new FFprobe(ffprobeDir);
        FFmpegProbeResult probeResult = ffprobe.probe(videoPath);

        AtomicInteger counter = new AtomicInteger(0);

        // subtitle 타입 스트림만 골라 Record로 맵핑
        return probeResult.getStreams().stream()
                .filter(stream -> stream.codec_type == CodecType.SUBTITLE)
                .map(stream -> new SubtitleInfo(
                        "v"+ counter.getAndIncrement(), // AtomicInteger를 이용해 스트림 내 가변변수 사용
                        // tags가 null일 수 있으니 체크
                        stream.tags != null ? stream.tags.get("language") : null
                ))
                .collect(Collectors.toList());
    }

    public InputStream executeCommand(ProcessBuilder processBuilder) throws IOException{
        // 에러 출력 널라우팅
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            processBuilder.redirectError(new File("NUL")); // Windows
        } else {
            processBuilder.redirectError(new File("/dev/null")); // Unix/Linux
        }
        Process process = processBuilder.start(); // 프로세스 실행

        // 종료 코드 확인
        return process.getInputStream();
    }



    @Override
    public List<List<String>> getVideoKeyFrame(String videoPath) throws IOException {
        try {
            // 비디오 파일 경로
//            String videoPath = new ClassPathResource("video/" + filename).getFile().getPath();
            log.info("Video path: {}", videoPath);
            // FFprobe 명령어 생성
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffprobeDir,
                    "-select_streams" , "v:0",
                    "-skip_frame", "nokey",
                    "-show_entries" , "frame=pts_time,pkt_pos", // 각 키 프레임 시간, 해당 바이트 위치
                    "-of", "csv",
                    videoPath
            );
            InputStream inputStream = executeCommand(processBuilder);

            // 프로세스 출력
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            // 종료 코드 확인
            return parseFrames(output.toString()); // 프로세스 출력 반환
        } catch (Exception e) {
            log.error("FFprobe 에러: {}", e.getMessage(), e);
            throw new IOException("FFprobe 실행 에러", e);
        }
    }

    @Override
    public Double getVideoDuration(String videoPath) throws IOException {
        FFprobe ffprobe = new FFprobe(ffprobeDir);
//        String videoPath = new ClassPathResource("video/" + filename).getFile().getPath();
        FFmpegProbeResult probeResult = ffprobe.probe(videoPath);

        return probeResult.getFormat().duration;
    }

    public List<List<String>> parseFrames(String frames){
        String[] frameArray = frames.split("\n");

        List<List<String>> result = new ArrayList<>();
        for (String frame : frameArray) {
            String[] data = frame.split(",");
            List<String> frameList = List.of(data);
            result.add(frameList);
        }
        return result;

    }

    @Override
    public InputStreamResource getInitData(String videoPath) throws IOException{
        try {
            // 비디오 파일 경로
//            String videoPath = new ClassPathResource("video/" + filename).getFile().getPath();
            log.info("Video path: {}", videoPath);
            // FFmpeg 명령어 생성

            List<String> command = new ArrayList<>(Arrays.asList(
                    ffmpegDir,                           // ffmpeg 실행 파일 경로
                    "-i", videoPath,                     // 입력 비디오 파일 경로
                    "-c:v", "libx264",                   // 비디오 코덱
                    "-c:a", "aac",                       // 오디오 코덱
                    "-f", "hls",                         // 출력 형식: HLS
                    "-hls_time", "10",                   // 세그먼트 길이: 10초
                    "-t", "0.01",                        // 출력 길이: 0.01초 (초기화 파일만 생성)
                    "-hls_segment_type", "fmp4",         // 세그먼트 형식: fMP4
                    "-hls_fmp4_init_filename", "pipe:1", // 초기화 파일을 표준 출력으로 전송
//                    "-hls_flags", "single_file",         // 단일 파일로 저장
                    "-hls_playlist_type", "vod",         // VOD 모드 설정
                    "-hls_segment_filename", "\"NUL %03d.m4s\"",   // 세그먼트를 파이프 출력
                    "NUL"                                // 플레이리스트 파일 생성 방지
            ));

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            return new InputStreamResource(executeCommand(processBuilder)); // 프로세스 출력 반환
        } catch (Exception e) {
            log.error("FFmpeg 에러: {}", e.getMessage(), e);
            throw new IOException("FFmpeg 실행 에러", e);
        }
    }

    public Flux<DataBuffer> getTsData(String videoPath, String start, String to) {
        return getTsData(videoPath, start, to, "0");
    }


    public Flux<DataBuffer> getTsData(String videoPath, String start, String to, String type) {
        return Flux.defer(() -> {
                    List<String> command = new ArrayList<>(Arrays.asList(
                            ffmpegDir,
                            "-ss", start,
                            "-i", videoPath,
                            "-c:v", "libx264",
                            "-preset", "fast",
                            "-to", to,
                            "-c:a", "aac",
                            "-f", "mpegts",
                            "-muxdelay", "0.1",
                            "-copyts",
                            "pipe:1"
                    ));

                    VideoResolutionEnum resolution = VideoResolutionEnum.fromType(type)
                            .orElse(null);

                    // 해상도 옵션 동적으로 추가
                    if (resolution != null) {
                        int insertPos = command.indexOf("-c:v") + 2;

                        switch (resolution) {
                            case RES_480P -> {
                                command.add(insertPos,"-vf");
                                command.add(insertPos + 1, "scale=-2:480");
                            } case RES_720P -> {
                                command.add(insertPos,"-vf");
                                command.add(insertPos + 1, "scale=-2:720");
                            } case RES_1080P -> {
                                command.add(insertPos,"-vf");
                                command.add(insertPos + 1, "scale=-2:1080");
                            } case RES_1440P -> {
                                command.add(insertPos,"-vf");
                                command.add(insertPos + 1, "scale=-2:1440");
                            }
                        }
                    }

                    ProcessBuilder pb = new ProcessBuilder(command);
                    Process process;
                    try {
                        process = pb.start();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    // stderr 로그를 별도 스레드에서 버려서 FFmpeg가 블록되지 않도록 구현
                    Schedulers.boundedElastic().schedule(() -> {
                        try (InputStream err = process.getErrorStream()) {
                            byte[] buf = new byte[1024]; // 버퍼 크기
                            while (err.read(buf) != -1) { /* 버퍼 비움 */ }
                        } catch (IOException ignored) { }
                    });

                    // stdout은 DataBufferUtils 로 읽어서 Flux<DataBuffer>로 변환
                    Flux<DataBuffer> videoFlux = DataBufferUtils.readInputStream(
                            process::getInputStream,
                            dataBufferFactory,
                            4096
                    );

                    return videoFlux
                            .doFinally(sig -> {
                                if (process.isAlive()) process.destroyForcibly();
                            });
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public InputStreamResource getFmp4Data(String videoPath, String start, String to, String type) throws IOException {
        try {
            // 비디오 파일 경로
//            String videoPath = new ClassPathResource("video/" + filename).getFile().getPath();
            log.info("Video path: {}", videoPath);

            // FFmpeg 명령어 생성
            List<String> command = new ArrayList<>(Arrays.asList(
                    ffmpegDir,
                    "-ss", start,
//                    "-itsoffset", start,
                    "-i", videoPath,
                    "-c:v", "libx264",                   // 비디오 코덱
                    "-c:a", "aac",                       // 오디오 코덱
                    "-f", "hls",                         // 출력 형식: HLS
                    "-hls_time", "10",                   // 세그먼트 길이: 10초
                    "-hls_segment_type", "fmp4",         // 세그먼트 형식: fMP4
                    "-to", "10",                          // 종료 시간: 10초 (출력 길이)
                    "-muxdelay", "0.1",
//                    "-copyts",
//                    "-hls_flags", "single_file",         // 단일 파일로 저장
                    "-hls_fmp4_init_filename", "NUL",
                    "-hls_playlist_type", "vod",         // VOD 모드 설정
                    "-reset_timestamps","1",
                    "-hls_segment_filename", "\"pipe:1_%03d.m4s\"",   // 세그먼트를 파이프 출력
                    "NUL"                                // 플레이리스트 파일 출력 방지
            ));

            // 해상도 옵션 동적으로 추가
//            if (type.equals("1")) {
//                command.add(command.indexOf("-c:v") + 2, "-vf");
//                command.add(command.indexOf("-c:v") + 3, "scale=-2:480");
//            } else if (type.equals("2")) {
//                command.add(command.indexOf("-c:v") + 2, "-vf");
//                command.add(command.indexOf("-c:v") + 3, "scale=-2:720");
//            } else if (type.equals("3")) {
//                command.add(command.indexOf("-c:v") + 2, "-vf");
//                command.add(command.indexOf("-c:v") + 3, "scale=-2:1080");
//            } else if (type.equals("4")) {
//                command.add(command.indexOf("-c:v") + 2, "-vf");
//                command.add(command.indexOf("-c:v") + 3, "scale=-2:1440");
//            }

            log.info("command: {}", command);

            // ProcessBuilder 생성
            ProcessBuilder processBuilder = new ProcessBuilder(command);

            return new InputStreamResource(executeCommand(processBuilder)); // 프로세스 출력 반환
        } catch (Exception e) {
            log.error("FFmpeg 에러: {}", e.getMessage(), e);
            throw new IOException("FFmpeg 실행 에러", e);
        }
    }

    public Flux<DataBuffer> getSubtitleFromVideo(String videoPath, String subtitleId){
        return Flux.defer(() -> {
            log.info("### FFmpegServiceProcessImpl.getSubtitleFromVideo. videoPath: {}, subtitleId: {}", videoPath, subtitleId);
            List<String> command = new ArrayList<>(Arrays.asList(
                    ffmpegDir,
                    "-i", videoPath,
                    "-vn", "-an", // 비디오, 오디오 스트림 제외
                    "-map", "0:s:"+subtitleId,
                    "-c:s", "ass",
                    "-f", "ass",
                    "pipe:1"
            ));

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process;
            try {
                process = pb.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // stderr 로그를 별도 스레드에서 버려서 FFmpeg가 블록되지 않도록 구현
            Schedulers.boundedElastic().schedule(() -> {
                try (InputStream err = process.getErrorStream()) {
                    byte[] buf = new byte[1024]; // 버퍼 크기
                    while (err.read(buf) != -1) { /* 버퍼 비움 */ }
                } catch (IOException ignored) { }
            });

            // stdout은 DataBufferUtils 로 읽어서 Flux<DataBuffer>로 변환
            Flux<DataBuffer> videoFlux = DataBufferUtils.readInputStream(
                    process::getInputStream,
                    dataBufferFactory,
                    4096
            );

            return videoFlux
                    .doFinally(sig -> {
                        if (process.isAlive()) process.destroyForcibly();
                    });
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Boolean> convertSubtitleToAss(String inputPath, String outputPath, String fontName, int fontSize, String charEncoding) {
        return Flux.defer(() -> {
                    log.info("### FFmpegServiceProcessImpl.convertSubtitleToAss. inputPath: {}, outputPath: {}, fontName: {}, fontSize: {}, charEncoding: {}", inputPath, outputPath, fontName, fontSize, charEncoding);
                    List<String> command = new ArrayList<>(Arrays.asList(
                            ffmpegDir,
                            "-sub_charenc", charEncoding,
                            "-i", inputPath,
                            "-c:s", "ass",
                            "-f", "ass",
                            "pipe:1"
                    ));

                    ProcessBuilder pb = new ProcessBuilder(command);
                    Process process;
                    try {
                        process = pb.start();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    // stderr 로그를 별도 스레드에서 버려서 FFmpeg가 블록되지 않도록 구현
                    Schedulers.boundedElastic().schedule(() -> {
                        try (InputStream err = process.getErrorStream()) {
                            byte[] buf = new byte[1024]; // 버퍼 크기
                            while (err.read(buf) != -1) { /* 버퍼 비움 */ }
                        } catch (IOException ignored) { }
                    });

                    // stdout은 DataBufferUtils 로 읽어서 Flux<DataBuffer>로 변환
                    Flux<DataBuffer> videoFlux = DataBufferUtils.readInputStream(
                            process::getInputStream,
                            dataBufferFactory,
                            4096
                    );

                    return videoFlux
                            .doFinally(sig -> {
                                if (process.isAlive()) process.destroyForcibly();
                            });
                })
                .subscribeOn(Schedulers.boundedElastic())
                // pipe:1로 받은 데이터를 문자열로 변환하고 수정
                .reduce(dataBufferFactory.wrap(new byte[0]), (buffer1, buffer2) -> {
                    // 두 버퍼를 합치기
                    byte[] bytes1 = new byte[buffer1.readableByteCount()];
                    byte[] bytes2 = new byte[buffer2.readableByteCount()];
                    buffer1.read(bytes1);
                    buffer2.read(bytes2);

                    byte[] combined = new byte[bytes1.length + bytes2.length];
                    System.arraycopy(bytes1, 0, combined, 0, bytes1.length);
                    System.arraycopy(bytes2, 0, combined, bytes1.length, bytes2.length);

                    // 버퍼 해제
                    DataBufferUtils.release(buffer1);
                    DataBufferUtils.release(buffer2);

                    return dataBufferFactory.wrap(combined);
                })
                .map(buffer -> {
                    // DataBuffer를 문자열로 변환
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    DataBufferUtils.release(buffer);

                    // Style 라인 수정
                    String modifiedContent = content.replaceAll(
                            "(Style:[^,]*,)Arial(,[0-9]+)",
                            "$1" + fontName + "," + fontSize
                    );

                    return modifiedContent;
                })
                .flatMap(content -> {
                    // 수정된 내용을 파일로 저장
                    DataBuffer buffer = dataBufferFactory.wrap(content.getBytes(StandardCharsets.UTF_8));
                    return DataBufferUtils.write(Flux.just(buffer), Paths.get(outputPath));
                })
                .then(Mono.just(true))  // 성공시 true 반환
                .onErrorReturn(false);  // 실패시 false 반환
    }

}