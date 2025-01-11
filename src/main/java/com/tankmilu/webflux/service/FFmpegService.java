package com.tankmilu.webflux.service;

import java.io.IOException;
import java.util.List;

public interface FFmpegService {

    String getVideoKeyFrame(String filename) throws IOException;

    List<List<String>> parseFrames(String frames);
}
