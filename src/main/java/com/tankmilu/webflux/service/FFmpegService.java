package com.tankmilu.webflux.service;

import java.io.IOException;

public interface FFmpegService {

    String getVideoKeyFrame(String filename) throws IOException;
}
