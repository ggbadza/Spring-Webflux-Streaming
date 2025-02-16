package com.tankmilu.webflux.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.filesystem.urls.base}")
@RequiredArgsConstructor
public class FileSystemController {
}
