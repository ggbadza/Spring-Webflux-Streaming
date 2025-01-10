package com.tankmilu.webflux.service;
import com.tankmilu.webflux.record.VideoMonoRecord;

import java.io.IOException;

public interface VideoService {

    // 단일 Video 청크를 리턴하는 메소드
    VideoMonoRecord getVideoChunk(String name, String rangeHeader);

    String getHlsOriginal(String filename) throws IOException;
}
