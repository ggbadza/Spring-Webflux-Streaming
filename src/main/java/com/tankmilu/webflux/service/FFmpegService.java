package com.tankmilu.webflux.service;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

// 구현 방식에 따라 스트림기반, 파일기반으로 구현 가능
public interface FFmpegService {

    HashMap<String,String> getVideoMetaData(String videoPath) throws IOException;

    List<List<String>> getVideoKeyFrame(String videoPath) throws IOException;

    Double getVideoDuration(String videoPath) throws IOException;

    InputStreamResource getInitData(String videoPath) throws IOException;

    Flux<DataBuffer> getTsData(String videoPath, String start, String end) throws IOException;
    Flux<DataBuffer> getTsData(String videoPath, String start, String end, String type) throws IOException;

    InputStreamResource getFmp4Data(String videoPath, String start, String end, String type) throws IOException;
}
