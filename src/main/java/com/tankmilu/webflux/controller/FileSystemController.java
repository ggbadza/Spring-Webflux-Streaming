package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.DirectoryRecord;
import com.tankmilu.webflux.record.VideoFileRecord;
import com.tankmilu.webflux.service.FileSystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("${app.filesystem.urls.base}")
@RequiredArgsConstructor
public class FileSystemController {

    private final FileSystemService fileSystemService;

    @GetMapping("${app.filesystem.urls.files}")
    public Mono<List<DirectoryRecord>> getDirectoryContents(
            @RequestParam(required = false, defaultValue = "0") Long pid) {
        return fileSystemService.getFolderAndFilesList(pid);
    }

    @PostMapping("${app.filesystem.urls.videoinfo}")
    public Mono<VideoFileRecord> getVideoFileInfo(
            @RequestParam(required = false, defaultValue = "0") Long pid,
            @RequestParam String fn) {
        return fileSystemService.getVideoFileInfo(pid, fn);
    }

}
