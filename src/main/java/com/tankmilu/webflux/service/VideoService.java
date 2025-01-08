package com.tankmilu.webflux.service;
import com.tankmilu.webflux.record.VideoMonoRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;

public interface VideoService {

    // 단일 Video 청크를 리턴하는 메소드
    public VideoMonoRecord getVideoChunk(String name, String rangeHeader);

    public String getHlsOriginal(String filename) throws IOException;
}
