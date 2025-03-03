package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.DirectoryRecord;
import com.tankmilu.webflux.record.VideoFileRecord;
import com.tankmilu.webflux.security.CustomUserDetails;
import com.tankmilu.webflux.service.FileSystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @RequestParam(required = false, defaultValue = "0") Long pid,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return fileSystemService.getFolderAndFilesList(pid, userDetails.getSubscriptionCode());
    }

    @PostMapping("${app.filesystem.urls.videoinfo}")
    public Mono<VideoFileRecord> getVideoFileInfo(
            @RequestParam(required = false, defaultValue = "0") Long pid,
            @RequestParam String fn,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return fileSystemService.getVideoFileInfo(pid, fn, userDetails.getSubscriptionCode());
    }

}
