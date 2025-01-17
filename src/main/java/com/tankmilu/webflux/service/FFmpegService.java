package com.tankmilu.webflux.service;

import org.springframework.core.io.InputStreamResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public interface FFmpegService {

    HashMap<String,String> getVideoMetaData(String filename) throws IOException;

    List<List<String>> getVideoKeyFrame(String filename) throws IOException;

    Double getVideoDuration(String filename) throws IOException;

    InputStreamResource getInitData(String filename) throws IOException;

    InputStreamResource getTsData(String filename, String start, String end) throws IOException;
    InputStreamResource getTsData(String filename, String start, String end, String type) throws IOException;
}
