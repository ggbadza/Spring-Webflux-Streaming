package com.tankmilu.webflux.service;

import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
public class FFmpegServiceProcessImpl implements FFmpegService {

    @Value("${custom.ffmpeg.ffmpeg}")
    public String ffmpegDir;

    @Value("${custom.ffmpeg.ffprobe}")
    public String ffprobeDir;

    @Override
    public HashMap<String, String> getVideoMetaData(String filename) throws IOException {
        FFprobe ffprobe = new FFprobe(ffprobeDir);
        String videoPath = new ClassPathResource("video/" + filename).getFile().getPath();
        FFmpegProbeResult probeResult = ffprobe.probe(videoPath);

        HashMap<String, String> metaData = new HashMap<>();

        // 해시맵에 입력
        metaData.put("format", probeResult.getFormat().format_name);
        metaData.put("duration", String.valueOf(probeResult.getFormat().duration));
        metaData.put("bitrate", String.valueOf(probeResult.getFormat().bit_rate));
        metaData.put("size", String.valueOf(probeResult.getFormat().size));
        log.info("ffmpeg probe result: {}", probeResult);
        return metaData;
    }

    @Override
    public List<List<String>> getVideoKeyFrame(String filename) throws IOException {
        try {
            // 비디오 파일 경로
            String videoPath = new ClassPathResource("video/" + filename).getFile().getPath();
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
            // 에러 출력 널라우팅
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder.redirectError(new File("NUL")); // Windows
            } else {
                processBuilder.redirectError(new File("/dev/null")); // Unix/Linux
            }
            log.info("Process builder: {}", processBuilder);
            Process process = processBuilder.start(); // 프로세스 실행
            log.info("Process started: {}", process);

            // 프로세스 출력
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
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
    public Double getVideoDuration(String filename) throws IOException {
        FFprobe ffprobe = new FFprobe(ffprobeDir);
        String videoPath = new ClassPathResource("video/" + filename).getFile().getPath();
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
    public InputStreamResource getInitData(String filename) throws IOException{
        try {
            // 비디오 파일 경로
            String videoPath = new ClassPathResource("video/" + filename).getFile().getPath();
            log.info("Video path: {}", videoPath);
            // FFmpeg 명령어 생성
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegDir,
                    "-i", videoPath,
                    "-an",
                    "-vn",
                    "-c:v", "copy",           // 비디오 스트림 복사
                    "-f", "mp4",
                    "-movflags", "frag_keyframe+empty_moov+separate_moof",
//                    "-t", "0",                // 비디오 데이터 제외
                    "pipe:1"
            );
            // 에러 출력 널라우팅
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder.redirectError(new File("NUL")); // Windows
            } else {
                processBuilder.redirectError(new File("/dev/null")); // Unix/Linux
            }
            log.info("Process builder: {}", processBuilder);
            Process process = processBuilder.start(); // 프로세스 실행
            log.info("Process started: {}", process);

            // 프로세스 출력
            InputStream inputStream = process.getInputStream();
            InputStreamResource resource = new InputStreamResource(inputStream);

            // 종료 코드 확인
            return resource; // 프로세스 출력 반환
        } catch (Exception e) {
            log.error("FFmpeg 에러: {}", e.getMessage(), e);
            throw new IOException("FFmpeg 실행 에러", e);
        }
    }

    @Override
    public InputStreamResource getTsData(String filename, String start, String to) throws IOException {
        try {
            // 비디오 파일 경로
            String videoPath = new ClassPathResource("video/" + filename).getFile().getPath();
            log.info("Video path: {}", videoPath);
            // FFmpeg 명령어 생성
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegDir,
                    "-ss", start, // ss가 앞에 있어야 인코딩 보다 오프셋 위치 먼저 찾아감
                    "-i", videoPath,
                    "-to", to,
//                    "-to", Integer.valueOf(Integer.valueOf(start)+10).toString(),
                    "-c:v", "libx264",
                    "-preset", "fast",
                    "-c:a", "aac",
                    "-f", "mpegts",
                    "-muxdelay", "0.1",        // 타임스탬프 동기화
                    "-copyts",                 // 원본 타임스탬프 유지
                    "pipe:1"
            );
            // 에러 출력 널라우팅
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder.redirectError(new File("NUL")); // Windows
            } else {
                processBuilder.redirectError(new File("/dev/null")); // Unix/Linux
            }
            log.info("Process builder: {}", processBuilder);
            Process process = processBuilder.start(); // 프로세스 실행
            log.info("Process started: {}", process);

            // 프로세스 출력
            InputStream inputStream = process.getInputStream();
            InputStreamResource resource = new InputStreamResource(inputStream);

            // 종료 코드 확인
            return resource; // 프로세스 출력 반환
        } catch (Exception e) {
            log.error("FFmpeg 에러: {}", e.getMessage(), e);
            throw new IOException("FFmpeg 실행 에러", e);
        }
    }

}