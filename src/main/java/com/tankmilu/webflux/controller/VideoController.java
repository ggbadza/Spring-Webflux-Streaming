package com.tankmilu.webflux.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/video")
@Slf4j
public class VideoController {

    @GetMapping("/test")
    public Mono<String> getVideo() {
        return Mono.just("test");
    }
}
