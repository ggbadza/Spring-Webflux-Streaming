package com.tankmilu.webflux.service;

import com.tankmilu.webflux.record.VideoMonoRecord;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VideoServiceImpl implements VideoService  {

    private static final int CHUNK_SIZE = 1024 * 1024;

    @Value("${custom.ffmpeg.ffmpeg}")
    public String ffmpegDir;

    @Value("${custom.ffmpeg.ffprobe}")
    public String ffprobeDir;

    public VideoMonoRecord getVideoChunk(String name, String rangeHeader){
        Path videoPath;

        String contentType = null;
        String rangeRes = null;
        long contentLength = 0;

        try {
            videoPath = new ClassPathResource("video/" + name).getFile().toPath(); // 비디오 파일 경로
        } catch (Exception e) {
            log.error(e.toString());
            return new VideoMonoRecord(contentType,rangeRes,contentLength,Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found")));
        }


        long fileLength = 0;
        try {
            fileLength = Files.size(videoPath);
            contentType = Optional.ofNullable(Files.probeContentType(videoPath)).orElse("video/mp4");
        } catch (IOException e) {
            log.error(e.toString());
            return new VideoMonoRecord(contentType,rangeRes,contentLength,Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "IO Error")));
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
        long finalEnd = Math.min(start + CHUNK_SIZE - 1,end);
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
        return new VideoMonoRecord(contentType,rangeRes,contentLength,videoMono);
    }

    public String getHlsOriginal (String filename) throws IOException {
            try {
                // 비디오 파일 경로
                File videoPath = new ClassPathResource("video/" + filename).getFile();
                log.info("Video path: {}", videoPath.getPath());
                // FFprobe 명령어 생성
                ProcessBuilder processBuilder = new ProcessBuilder(
                        ffprobeDir,
                                "-select_streams" , "v:0",
                                "-skip_frame", "nokey",
                                "-show_entries" , "frame=pts_time,pkt_pos",
                                "-of", "csv",
                                videoPath.getPath()
                );
                processBuilder.redirectErrorStream(true); // 표준출력과 에러 같이 출력
                log.info("Process builder: {}", processBuilder);
                Process process = processBuilder.start(); // 프로세스 실행
                log.info("Process started: {}", process);
//                processBuilder.redirectError(new File("C:/app/null.txt"));

                // 프로세스 출력
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("frame")) { // frame 라인만 출력
                        output.append(line).append("\n");
                    }
                }

                // 종료 코드 확인
                int exitCode = process.waitFor();
                return output.toString(); // 프로세스 출력 반환
            } catch (Exception e) {
                log.error("FFprobe 에러: {}", e.getMessage(), e);
                throw new IOException("FFprobe 실행 에러", e);
            }

    }

}
