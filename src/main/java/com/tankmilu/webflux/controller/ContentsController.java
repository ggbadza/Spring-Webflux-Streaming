package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.record.*;
import com.tankmilu.webflux.security.CustomUserDetails;
import com.tankmilu.webflux.service.ContentsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
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
     * @param contentsId 컨텐츠 ID
     * @return 컨텐츠 정보가 포함된 응답 객체(ContentsReponse)를 반환
     *
     */
    @RequestMapping("${app.contents.urls.info}")
    public Mono<ContentsResponse> getVideoFileInfo(
            @RequestParam Long contentsId) {
        return contentsService.getContentsInfo(contentsId);
    }

    /**
     * 가장 최근에 변경된 컨텐츠 목록을 조회함(파라미터가 null 일시 전체 목록중에 20개)
     *
     * @param type 컨텐츠 타입(anime, moive, drama)
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
     * @param userDetails 로그인 한 사용자 정보
     * @return 추천 컨텐츠 정보가 포함된 응답 객체 Flux(RecommendContentsResponse)를 반환
     *
     */
    @RequestMapping("${app.contents.urls.recommend}")
    public Flux<RecommendContentsResponse> getRecommendContents(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return contentsService.getRecommendContents(userDetails.getUsername());
    }

    /**
     * 각 컨텐츠에 속한 파일들을 리턴
     *
     * @param contentsId 조회 할 컨텐츠ID
     * @return 추천 컨텐츠 정보가 포함된 응답 객체 Flux(RecommendContentsResponse)를 반환
     *
     */
    @RequestMapping("${app.contents.urls.files}")
    public Flux<FileInfoSummaryResponse> getContentsFiles(
            @RequestParam Long contentsId) {
        return contentsService.getContentsFiles(contentsId);
    }

    /**
     * 컨텐츠 파일의 정보와 각 컨텐츠에 속한 비디오 파일을 조회함
     * (비디오 시청 페이지용 API)
     *
     * @param fileId 파일 ID
     * @return 컨텐츠 정보와 비디오 파일 리스트가 포함된 응답 객체를 반환
     *
     */
    @RequestMapping("${app.contents.urls.contents_info_with_video_files}")
    public Mono<ContentsInfoWithFilesResponse> getContentsInfoWithVideoFiles(
            @RequestParam Long fileId) {
        return contentsService.getContentsInfoWithVideoFiles(fileId);
    }

    /**
     * 검색어를 통해 엘라스틱서치에서 콘텐츠를 조회합니다.
     * 제목, 설명, 키워드 필드에서 검색어가 포함된 콘텐츠를 찾습니다.
     *
     * @param contentsSearchRequest 검색어
     * @return 검색 결과로 ContentsObjectDocument 리스트를 반환하는 Flux
     */
    @PostMapping("${app.contents.urls.search}")
    public Flux<ContentsSearchResponse> searchContentsByKeyword(@RequestBody ContentsSearchRequest contentsSearchRequest) {
        return contentsService.searchContentsByQuery(contentsSearchRequest.query());
    }

    /**
     * 특정 컨텐츠를 즐겨찾기 목록에 추가합니다.
     *
     * @param userDetails 유저 계정 정보
     * @param contentsId 등록 할 컨텐츠 ID
     * @return Boolean
     */
    @RequestMapping(value = "${app.contents.urls.register_following}", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<Boolean> registerFollowing(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long contentsId) {
        return contentsService.registerFollowing(userDetails.getUsername(),contentsId);
    }

    /**
     * 특정 컨텐츠를 즐겨찾기 목록에서 제거합니다.
     *
     * @param userDetails 유저 계정 정보
     * @param contentsId 삭제 할 컨텐츠 ID
     * @return Boolean
     */
    @RequestMapping(value = "${app.contents.urls.delete_following}", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<Boolean> deleteFollowing(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long contentsId) {
        return contentsService.deleteFollowing(userDetails.getUsername(),contentsId);
    }

    /**
     * 즐겨찾기한 컨텐츠를 가져옵니다.
     *
     * @param userDetails 유저 계정 정보
     * @return ContentsResponse 즐겨찾기 한 컨텐츠 정보
     */
    @GetMapping("${app.contents.urls.get_following}")
    public Flux<ContentsResponse> getFollowingContents(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return contentsService.getFollowingContents(userDetails.getUsername());
    }

    /**
     * 해당 컨텐츠가 즐겨찾기 목록에 팔로잉한 컨텐츠 유무를 체크합니다.
     *
     * @param userDetails 유저 계정 정보
     * @param contentsId 체크 할 컨텐츠 Id
     * @return Boolean 컨텐츠 팔로잉 유무
     */
    @RequestMapping(value = "${app.contents.urls.is_following}", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<Boolean> isFollowingByContentsId(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long contentsId) {
        return contentsService.isFollowingContent(userDetails.getUsername(), contentsId);
    }

    /**
     *  배너용 컨텐츠들을 가져옵니다
     *
     * @param userDetails 유저 계정 정보
     * @return ContentsResponse 즐겨찾기 한 컨텐츠 정보
     */
    @RequestMapping(value = "${app.contents.urls.is_following}", method = {RequestMethod.GET, RequestMethod.POST})
    public Flux<FeaturedBannersResponse> getFeaturedBanners(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return contentsService.getFeaturedBanners();
    }

}
