package com.tankmilu.webflux.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FFmpegServiceProcessImpl implements FFmpegService {

    @Value("${custom.ffmpeg.ffmpeg}")
    public String ffmpegDir;

    @Value("${custom.ffmpeg.ffprobe}")
    public String ffprobeDir;

    public String getVideoKeyFrame(String filename) throws IOException {
        try {
            // 비디오 파일 경로
            File videoPath = new ClassPathResource("video/" + filename).getFile();
            log.info("Video path: {}", videoPath.getPath());
            // FFprobe 명령어 생성
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffprobeDir,
                    "-select_streams" , "v:0",
                    "-skip_frame", "nokey",
                    "-show_entries" , "frame=pts_time,pkt_pos", // 각 키 프레임 시간, 해당 바이트 위치
                    "-of", "csv",
                    videoPath.getPath()
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
//                if (line.startsWith("frame")) { // frame 라인만 출력
                output.append(line).append("\n");
//                }
            }

            // 종료 코드 확인
            return output.toString(); // 프로세스 출력 반환
        } catch (Exception e) {
            log.error("FFprobe 에러: {}", e.getMessage(), e);
            throw new IOException("FFprobe 실행 에러", e);
        }
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
}