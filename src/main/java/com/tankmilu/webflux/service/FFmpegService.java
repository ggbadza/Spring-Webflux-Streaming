package com.tankmilu.webflux.service;

import org.springframework.core.io.InputStreamResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

// 구현 방식에 따라 스트림기반, 파일기반으로 구현 가능
public interface FFmpegService {

    HashMap<String,String> getVideoMetaData(String filename) throws IOException;

    List<List<String>> getVideoKeyFrame(String filename) throws IOException;

    Double getVideoDuration(String filename) throws IOException;

    InputStreamResource getInitData(String filename) throws IOException;

    InputStreamResource getTsData(String filename, String start, String end) throws IOException;
    InputStreamResource getTsData(String filename, String start, String end, String type) throws IOException;

    InputStreamResource getFmp4Data(String filename, String start, String end, String type) throws IOException;
}
