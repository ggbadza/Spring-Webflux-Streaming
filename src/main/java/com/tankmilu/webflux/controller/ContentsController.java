package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.ContentsReponse;
import com.tankmilu.webflux.service.ContentsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 컨텐츠 파일에 대한 정보를 요청하는 REST API 컨트롤러
 * 동영상, 애니메이션, 드라마 등 각종 미디어 컨텐츠의 메타데이터 조회 함
 */
@RestController
@RequestMapping("${app.contents.urls.base}")
@RequiredArgsConstructor
public class ContentsController {

    private final ContentsService contentsService;

    /**
     * 컨텐츠 파일의 세부 정보를 조회함
     * 
     * @param cid 컨텐츠 ID
     * @return 컨텐츠 정보가 포함된 응답 객체(ContentsReponse)를 반환
     *
     */
    @PostMapping("${app.contents.urls.info}")
    public Mono<ContentsReponse> getVideoFileInfo(
            @RequestParam Long cid) {
        return contentsService.getContentsInfo(cid);
    }
}
