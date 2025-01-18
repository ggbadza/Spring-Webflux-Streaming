package com.tankmilu.webflux.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Map;

@SpringBootTest
public class VideoServiceTest {

    @Autowired
    private VideoService videoService;

    @Autowired
    private FFmpegService ffmpegService;

    @Test
    void getHlsM3u8Test() throws IOException {
        Map<String, String> metaData = ffmpegService.getVideoMetaData("video.mp4");

        System.out.println(metaData);

    }


}
