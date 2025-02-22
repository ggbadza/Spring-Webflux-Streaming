package com.tankmilu.webflux.record;

public record VideoFileRecord(
        Long pId, // 부모 폴더 id
        String fileName,
        String videoPath,
        String subtitlePath,
        String resolution
        ) {
}
