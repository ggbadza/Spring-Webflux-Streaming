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

    /**
     * 디렉토리 내용을 조회함
     * 
     * @param type 컨텐츠 유형 (anime, movie, drama 등)
     * @param pid 부모 디렉토리 ID (기본값: 0)
     * @param userDetails 인증된 사용자 정보
     * @return 디렉토리 내 폴더 및 파일 목록 반환
     */
    @GetMapping("${app.filesystem.urls.files}")
    public Mono<List<DirectoryRecord>> getDirectoryContents(
            @RequestParam(required = true) String type,
            @RequestParam(required = false, defaultValue = "0") Long pid,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return fileSystemService.getFolderAndFilesList(type, pid, userDetails.getSubscriptionCode());
    }

    /**
     * 비디오 파일의 상세 정보를 조회함
     * 
     * @param type 컨텐츠 유형 (anime, movie, drama 등)
     * @param pid 부모 디렉토리 ID (기본값: 0)
     * @param fn 파일명
     * @return 비디오 파일 상세 정보 반환
     */
    @PostMapping("${app.filesystem.urls.videoinfo}")
    public Mono<VideoFileRecord> getVideoFileInfo(
            @RequestParam(required = true) String type,
            @RequestParam(required = false, defaultValue = "0") Long pid,
            @RequestParam String fn,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return fileSystemService.getVideoFileInfo(type, pid, fn, userDetails.getSubscriptionCode());
    }

}
