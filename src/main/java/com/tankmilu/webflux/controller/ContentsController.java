package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.ContentsResponse;
import com.tankmilu.webflux.record.RecommendContentsResponse;
import com.tankmilu.webflux.service.ContentsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
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
    public Mono<ContentsResponse> getVideoFileInfo(
            @RequestParam Long cid) {
        return contentsService.getContentsInfo(cid);
    }

    /**
     * 가장 최근에 변경된 컨텐츠 목록을 조회함(파라미터가 null 일시 전체 목록중에 20개)
     *
     * @param type 컨텐츠 타입(aniem, moive, drama)
     * @param pid 조회 할 폴더ID
     * @return 컨텐츠 정보가 포함된 응답 객체 Flux(ContentsReponse)를 수정시간의 역순으로 반환
     *
     */
    @PostMapping("${app.contents.urls.info_recently}")
    public Flux<ContentsResponse> getContentsInfoRecently(
            @RequestParam String type,
            @RequestParam Long pid) {
        return contentsService.getContentsInfoRecently(type, pid);
    }

    /**
     * 각 사용자 ID 별 추천 컨텐츠 정보를 리턴
     *
     * @param userId 유저 ID
     * @return 추천 컨텐츠 정보가 포함된 응답 객체 Flux(RecommendContentsResponse)를 반환
     *
     */
    @PostMapping("${app.contents.urls.recommend}")
    public Flux<RecommendContentsResponse> getRecommendContents(
            @RequestParam(defaultValue = "default") String userId) {
        return contentsService.getRecommendContents(userId);
    }


}
