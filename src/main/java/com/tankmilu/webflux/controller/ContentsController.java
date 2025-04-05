package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.ContentsReponse;
import com.tankmilu.webflux.service.ContentsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${app.contents.urls.base}")
@RequiredArgsConstructor
public class ContentsController {

    private final ContentsService contentsService;

    @PostMapping("${app.contents.urls.info}")
    public Mono<ContentsReponse> getVideoFileInfo(
            @RequestParam Long cid) {
        return contentsService.getContentsInfo(cid);
    }
}
